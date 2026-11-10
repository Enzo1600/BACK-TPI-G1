package com.auxiruta.gateway.integration;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

/** Opt-in administrative repair for realms initialized with the original phase 2. */
@EnabledIfSystemProperty(named = "auxiruta.repairIdentity", matches = "true")
class KeycloakScopeSetupTest {
  @Test
  void ensureBasicScopeWithoutDeletingRealm() throws Exception {
    Path directory = Path.of("").toAbsolutePath();
    if (!Files.exists(directory.resolve("docker-compose.yml"))) directory = directory.getParent();
    for (String line : Files.readAllLines(directory.resolve(".env")))
      if (!line.isBlank() && !line.startsWith("#")) {
        int i = line.indexOf('=');
        if (i > 0) ENV.put(line.substring(0, i), line.substring(i + 1));
      }
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
    String scope = null;
    for (var s : admin("GET", "/client-scopes", null, 200))
      if (s.path("name").asText().equals("basic")) scope = s.path("id").asText();
    assertNotNull(scope);
    for (String client : java.util.List.of("auxiruta-api", "assistance-service")) {
      String id = admin("GET", "/clients?clientId=" + client, null, 200).get(0).path("id").asText();
      admin("PUT", "/clients/" + id + "/default-client-scopes/" + scope, null, 204);
      boolean exists = false;
      for (var s : admin("GET", "/clients/" + id + "/default-client-scopes", null, 200))
        if (s.path("name").asText().equals("basic")) exists = true;
      assertTrue(exists);
    }
  }
}
