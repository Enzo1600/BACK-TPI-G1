package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only client: real local Keycloak PKCE, HTTP through Gateway and private Docker probes. */
final class BackendClient {
  static final ObjectMapper JSON = new ObjectMapper();
  static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  static final String ISSUER = "http://localhost:8080/realms/auxiruta";
  static final String OIDC = ISSUER + "/protocol/openid-connect";
  static final Map<String, String> ENV = new HashMap<>(), TOKENS = new HashMap<>();
  static final AtomicInteger checks = new AtomicInteger();
  static Path root;
  static String adminToken, extraUserId;

  static ObjectNode object(Object... pairs) {
    ObjectNode n = JSON.createObjectNode();
    for (int i = 0; i < pairs.length; i += 2)
      n.set(pairs[i].toString(), JSON.valueToTree(pairs[i + 1]));
    return n;
  }

  static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  static HttpResponse<String> send(HttpRequest request) throws Exception {
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  static HttpResponse<String> formPost(String url, Map<String, String> values, String cookies)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded");
    if (cookies != null && !cookies.isEmpty()) b.header("Cookie", cookies);
    return send(b.POST(HttpRequest.BodyPublishers.ofString(form(values))).build());
  }

  static JsonNode json(String text) throws Exception {
    return text.isBlank() ? JSON.nullNode() : JSON.readTree(text);
  }

  static JsonNode claims(String token) throws Exception {
    return JSON.readTree(Base64.getUrlDecoder().decode(token.split("[.]")[1]));
  }

  static void initialize() throws Exception {
    root = Path.of("").toAbsolutePath();
    if (!Files.exists(root.resolve("docker-compose.yml"))) root = root.getParent();
    for (String line : Files.readAllLines(root.resolve(".env")))
      if (!line.isBlank() && !line.startsWith("#")) {
        int i = line.indexOf('=');
        if (i > 0) ENV.put(line.substring(0, i), line.substring(i + 1));
      }
    var technical =
        formPost(
            OIDC + "/token",
            Map.of(
                "grant_type",
                "client_credentials",
                "client_id",
                "assistance-service",
                "client_secret",
                ENV.get("KEYCLOAK_CLIENT_SECRET")),
            null);
    assertEquals(200, technical.statusCode());
    TOKENS.put("TECH", json(technical.body()).get("access_token").asText());
    for (String role : List.of("DRIVER", "OPERATOR", "ADMIN"))
      TOKENS.put(role, login(role.toLowerCase() + ".demo", ENV.get("DEMO_" + role + "_PASSWORD")));
  }

  static String login(String user, String password) throws Exception {
    String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    String state = UUID.randomUUID().toString(), redirect = "http://127.0.0.1:8765/callback";
    String auth =
        OIDC
            + "/auth?"
            + form(
                Map.of(
                    "client_id",
                    "auxiruta-api",
                    "redirect_uri",
                    redirect,
                    "response_type",
                    "code",
                    "scope",
                    "openid",
                    "code_challenge",
                    challenge,
                    "code_challenge_method",
                    "S256",
                    "state",
                    state));
    var page =
        send(
            HttpRequest.newBuilder(URI.create(auth)).timeout(Duration.ofSeconds(20)).GET().build());
    assertEquals(200, page.statusCode());
    var matcher = Pattern.compile("<form[^>]*action=\"([^\"]+)\"").matcher(page.body());
    assertTrue(matcher.find(), "Expected Keycloak login form");
    String action = matcher.group(1).replace("&amp;", "&");
    assertEquals("localhost:8080", URI.create(action).getAuthority());
    // Localhost-only cookie forwarding emulates the browser's local HTTP login behavior.
    String cookies =
        page.headers().allValues("set-cookie").stream()
            .map(v -> v.split(";", 2)[0])
            .collect(java.util.stream.Collectors.joining("; "));
    var logged =
        formPost(
            action, Map.of("username", user, "password", password, "credentialId", ""), cookies);
    assertEquals(302, logged.statusCode(), "PKCE login redirect");
    URI callback = URI.create(logged.headers().firstValue("location").orElseThrow());
    assertEquals("127.0.0.1:8765", callback.getAuthority());
    Map<String, String> query = new HashMap<>();
    for (String item : callback.getRawQuery().split("&")) {
      String[] pair = item.split("=", 2);
      query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    assertEquals(state, query.get("state"));
    var token =
        formPost(
            OIDC + "/token",
            Map.of(
                "grant_type",
                "authorization_code",
                "client_id",
                "auxiruta-api",
                "redirect_uri",
                redirect,
                "code",
                query.get("code"),
                "code_verifier",
                verifier),
            null);
    assertEquals(200, token.statusCode());
    return json(token.body()).get("access_token").asText();
  }

  record Response(int status, JsonNode body) {}

  static Response raw(String role, String method, String path, JsonNode body, String key)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:8000" + path))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json");
    if (role != null) builder.header("Authorization", "Bearer " + TOKENS.get(role));
    if (key != null) builder.header("Idempotency-Key", key);
    var response =
        send(
            builder
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
    JsonNode data = json(response.body());
    if (response.statusCode() >= 400) {
      assertTrue(
          response
              .headers()
              .firstValue("content-type")
              .orElse("")
              .startsWith("application/problem+json"));
      assertEquals(response.statusCode(), data.path("status").asInt());
    }
    if (response.statusCode() == 201)
      assertTrue(response.headers().firstValue("location").orElse("").startsWith("/api/v1/"));
    checks.incrementAndGet();
    return new Response(response.statusCode(), data);
  }

  static JsonNode call(
      String role, String method, String path, JsonNode body, String key, int expected)
      throws Exception {
    var r = raw(role, method, path, body, key);
    assertEquals(expected, r.status(), method + " " + path);
    return r.body();
  }

  static JsonNode call(String role, String method, String path, JsonNode body, int expected)
      throws Exception {
    return call(role, method, path, body, null, expected);
  }

  static void direct(String service, int port, String path, String role, int expected)
      throws Exception {
    String config = "url = \"http://" + service + ":" + port + path + "\"\n";
    if (role != null) config += "header = \"Authorization: Bearer " + TOKENS.get(role) + "\"\n";
    Process p =
        new ProcessBuilder(
                "docker",
                "compose",
                "exec",
                "-T",
                "api-gateway",
                "curl",
                "-sS",
                "--max-time",
                "10",
                "--config",
                "-",
                "-w",
                "\n%{http_code}")
            .directory(root.toFile())
            .start();
    try (var out = p.getOutputStream()) {
      out.write(config.getBytes(StandardCharsets.UTF_8));
    }
    String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, p.waitFor());
    int split = result.lastIndexOf('\n');
    assertEquals(expected, Integer.parseInt(result.substring(split + 1)));
    if (expected >= 400)
      assertEquals(expected, json(result.substring(0, split)).path("status").asInt());
    else assertTrue(json(result.substring(0, split)).isArray());
    checks.incrementAndGet();
  }

  static JsonNode admin(String method, String path, JsonNode body, int expected) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:8080/admin/realms/auxiruta" + path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + adminToken)
            .header("Content-Type", "application/json");
    var response =
        send(
            b.method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
    assertEquals(expected, response.statusCode(), "Test identity setup/cleanup");
    return json(response.body());
  }

  static void secondDriver() throws Exception {
    var response =
        formPost(
            "http://localhost:8080/realms/master/protocol/openid-connect/token",
            Map.of(
                "grant_type",
                "password",
                "client_id",
                "admin-cli",
                "username",
                "bootstrap-admin",
                "password",
                ENV.get("KC_BOOTSTRAP_ADMIN_PASSWORD")),
            null);
    assertEquals(200, response.statusCode());
    adminToken = json(response.body()).path("access_token").asText();
    String username = "integration-" + UUID.randomUUID();
    String password = UUID.randomUUID() + "Aa1!";
    admin(
        "POST",
        "/users",
        object(
            "username",
            username,
            "firstName",
            "Integration",
            "lastName",
            "Driver",
            "email",
            username + "@example.test",
            "emailVerified",
            true,
            "enabled",
            true,
            "credentials",
            List.of(object("type", "password", "value", password, "temporary", false))),
        201);
    extraUserId =
        admin("GET", "/users?username=" + encode(username), null, 200).get(0).path("id").asText();
    JsonNode role = admin("GET", "/roles/DRIVER", null, 200);
    admin(
        "POST",
        "/users/" + extraUserId + "/role-mappings/realm",
        JSON.createArrayNode().add(role),
        204);
    TOKENS.put("OTHER_DRIVER", login(username, password));
  }

  static void cleanup() throws Exception {
    if (extraUserId != null) admin("DELETE", "/users/" + extraUserId, null, 204);
  }

  static String id(JsonNode n) {
    return n.path("id").asText();
  }

  static String key() {
    return UUID.randomUUID().toString();
  }
}
