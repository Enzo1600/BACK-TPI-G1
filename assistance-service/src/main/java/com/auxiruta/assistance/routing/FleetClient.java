package com.auxiruta.assistance.routing;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class FleetClient {
  public record Candidate(
      UUID towTruckId,
      UUID operatorId,
      String operatorSubject,
      BigDecimal capacityKg,
      Set<String> supportedTypes,
      boolean supportsNonRollable,
      String qualificationCategory,
      Instant qualificationValidFrom,
      Instant qualificationValidUntil,
      Instant availableSince,
      BigDecimal latitude,
      BigDecimal longitude,
      Instant locationUpdatedAt,
      double straightDistanceM) {}

  private final RestClient http;
  private final String base, tokenUrl, clientId, secret;
  private final Clock clock;
  private String token;
  private Instant expires = Instant.EPOCH;

  public FleetClient(
      @Value("${FLEET_URL:http://localhost:8081}") String base,
      @Value(
              "${KEYCLOAK_TOKEN_URI:http://localhost:8080/realms/auxiruta/protocol/openid-connect/token}")
          String tokenUrl,
      @Value("${KEYCLOAK_CLIENT_ID:assistance-service}") String clientId,
      @Value("${KEYCLOAK_CLIENT_SECRET:}") String secret,
      Clock clock) {
    this.base = base;
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.secret = secret;
    this.clock = clock;
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    http = RestClient.builder().requestFactory(factory).build();
  }

  private synchronized String token() {
    if (token != null && expires.isAfter(clock.instant().plusSeconds(10))) return token;
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    JsonNode response =
        http.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
    if (response == null || response.path("access_token").asText().isBlank())
      throw new IllegalStateException("FLEET_AUTH_UNAVAILABLE");
    token = response.path("access_token").asText();
    expires = clock.instant().plusSeconds(response.path("expires_in").asLong(0));
    return token;
  }

  public record Eligibility(
      UUID assignmentAttemptId,
      UUID requestId,
      UUID towTruckId,
      UUID operatorId,
      String operatorSubject,
      boolean eligible,
      String reason,
      BigDecimal latitude,
      BigDecimal longitude,
      Instant locationUpdatedAt,
      Instant qualificationValidUntil,
      Instant checkedAt) {}

  public Eligibility eligibility(UUID id) {
    return http.get()
        .uri(base + "/internal/reservations/" + id + "/eligibility")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
        .retrieve()
        .body(Eligibility.class);
  }

  public java.util.List<JsonNode> statuses(java.util.List<UUID> ids) {
    return http.post()
        .uri(base + "/internal/reservations/status-queries")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(java.util.Map.of("assignmentAttemptIds", ids))
        .retrieve()
        .body(
            new org.springframework.core.ParameterizedTypeReference<java.util.List<JsonNode>>() {});
  }

  public JsonNode reservation(UUID id, String kind, String payload) {
    if (kind.equals("CONFIRM")) {
      JsonNode existing =
          http.get()
              .uri(base + "/internal/reservations/" + id)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
              .retrieve()
              .body(JsonNode.class);
      if (existing != null
          && id.toString().equals(existing.path("assignmentAttemptId").asText())
          && !"HELD".equals(existing.path("status").asText())) return existing;
    }
    var request =
        kind.equals("RESERVE")
            ? http.put().uri(base + "/internal/reservations/" + id)
            : http.post()
                .uri(
                    base
                        + "/internal/reservations/"
                        + id
                        + (kind.equals("CONFIRM") ? "/confirmation" : "/release"));
    request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token());
    if (kind.equals("RESERVE")) request.contentType(MediaType.APPLICATION_JSON).body(payload);
    JsonNode result = request.retrieve().body(JsonNode.class);
    if (result == null || !id.toString().equals(result.path("assignmentAttemptId").asText()))
      throw new IllegalStateException("FLEET_INVALID_RESERVATION_RESPONSE");
    return result;
  }

  public List<Candidate> candidates(
      BigDecimal latitude,
      BigDecimal longitude,
      String type,
      BigDecimal weight,
      boolean nonRollable) {
    try {
      var uri =
          org.springframework.web.util.UriComponentsBuilder.fromUriString(
                  base + "/internal/fleet/candidates")
              .queryParam("latitude", latitude)
              .queryParam("longitude", longitude)
              .queryParam("vehicleType", type)
              .queryParam("weightKg", weight)
              .queryParam("nonRollable", nonRollable)
              .build()
              .toUri();
      List<Candidate> result =
          http.get()
              .uri(uri)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
              .retrieve()
              .body(new ParameterizedTypeReference<List<Candidate>>() {});
      if (result == null || result.size() > 5)
        throw new IllegalStateException("FLEET_INVALID_RESPONSE");
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("FLEET_UNAVAILABLE");
    }
  }
}
