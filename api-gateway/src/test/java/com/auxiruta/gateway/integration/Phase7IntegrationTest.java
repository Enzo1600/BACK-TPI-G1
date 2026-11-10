package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

@EnabledIfSystemProperty(named = "auxiruta.phase7", matches = "true")
class Phase7IntegrationTest {
  String truck, operator;
  List<String> requestIds = new ArrayList<>(), reservationIds = new ArrayList<>();

  @BeforeAll
  static void initializeClient() throws Exception {
    initialize();
  }

  @BeforeEach
  void resources() throws Exception {
    assertFalse(
        ENV.getOrDefault("ORS_API_KEY", "").isBlank(), "Real evaluation requires ORS_API_KEY");
    var op = raw("OPERATOR", "GET", "/api/v1/operators/me", null, null);
    operator =
        op.status() == 200
            ? id(op.body())
            : id(
                call(
                    "ADMIN",
                    "POST",
                    "/api/v1/operators",
                    object(
                        "keycloakSubject",
                        claims(TOKENS.get("OPERATOR")).path("sub").asText(),
                        "fullName",
                        "Phase 7 integration"),
                    201));
    assertEquals(
        404,
        raw("OPERATOR", "GET", "/api/v1/tow-trucks/assigned-to-me", null, null).status(),
        "Demo operator must have no assigned truck before this suite");
    call(
        "ADMIN",
        "PATCH",
        "/api/v1/operators/" + operator + "/registration-status",
        object("status", "ACTIVE"),
        200);
    call(
        "ADMIN",
        "PUT",
        "/api/v1/operators/" + operator + "/qualification",
        object(
            "category",
            "LIGHT_TOWING",
            "validFrom",
            Instant.now().minusSeconds(3600).toString(),
            "validUntil",
            Instant.now().plusSeconds(86400).toString()),
        200);
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "AVAILABLE"),
        200);
    truck =
        id(
            call(
                "ADMIN",
                "POST",
                "/api/v1/tow-trucks",
                object(
                    "licensePlate",
                    "TEST" + key().replace("-", "").substring(0, 6),
                    "maximumLoadKg",
                    3000,
                    "supportsNonRollable",
                    true,
                    "supportedTypes",
                    List.of("CAR")),
                201));
    call(
        "ADMIN",
        "PUT",
        "/api/v1/tow-trucks/" + truck + "/operator",
        object("operatorId", operator),
        200);
    call(
        "ADMIN",
        "PATCH",
        "/api/v1/tow-trucks/" + truck + "/operational-status",
        object("status", "ACTIVE", "reason", "Phase 7 fixture"),
        key(),
        200);
    call(
        "OPERATOR",
        "PUT",
        "/api/v1/tow-trucks/assigned-to-me/location",
        object("latitude", -31.4167, "longitude", -64.1833),
        200);
  }

  JsonNode request() throws Exception {
    String vehicle =
        id(
            call(
                "DRIVER",
                "POST",
                "/api/v1/vehicles",
                object(
                    "licensePlate",
                    "TST" + key().replace("-", "").substring(0, 7),
                    "vehicleType",
                    "CAR",
                    "declaredWeightKg",
                    1000),
                201));
    JsonNode request =
        call(
            "DRIVER",
            "POST",
            "/api/v1/assistance-requests",
            object(
                "vehicleId",
                vehicle,
                "originLatitude",
                -31.4167,
                "originLongitude",
                -64.1833,
                "destinationLatitude",
                -31.42,
                "destinationLongitude",
                -64.19,
                "mobilityCondition",
                "ROLLABLE",
                "description",
                "Phase 7 integration fixture"),
            key(),
            201);
    requestIds.add(id(request));
    return request;
  }

  JsonNode proposal(String request) throws Exception {
    for (int page = 0; page < 100; page++) {
      var result =
          call("OPERATOR", "GET", "/api/v1/operator/assignments?size=100&page=" + page, null, 200);
      for (var a : result.path("content"))
        if (a.path("requestId").asText().equals(request)) {
          reservationIds.add(id(a));
          return a;
        }
      if (page + 1 >= result.path("totalPages").asInt()) break;
    }
    fail("No proposal for request");
    return null;
  }

  @Test
  void exclusiveReservationAndIdempotence() throws Exception {
    String first = key(), second = key();
    reservationIds.addAll(List.of(first, second));
    var input =
        object(
            "requestId",
            key(),
            "towTruckId",
            truck,
            "operatorId",
            operator,
            "vehicleType",
            "CAR",
            "weightKg",
            1000,
            "nonRollable",
            false,
            "searchDeadline",
            Instant.now().plusSeconds(600).toString(),
            "estimatedServiceEndAt",
            Instant.now().plusSeconds(3600).toString());
    JsonNode a, b;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var x = pool.submit(() -> internal("PUT", first, input));
      var y = pool.submit(() -> internal("PUT", second, input));
      a = x.get();
      b = y.get();
    }
    assertEquals(
        List.of("EXPIRED", "HELD"),
        java.util.stream.Stream.of(a.path("status").asText(), b.path("status").asText())
            .sorted()
            .toList());
    String winner = a.path("status").asText().equals("HELD") ? first : second;
    assertEquals("HELD", internal("PUT", winner, input).path("status").asText());
    assertEquals(
        "CONFIRMED", internal("POST", winner + "/confirmation", null).path("status").asText());
    assertEquals(
        "CONFIRMED", internal("POST", winner + "/confirmation", null).path("status").asText());
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "UNAVAILABLE"),
        409);
    call("ADMIN", "DELETE", "/api/v1/tow-trucks/" + truck + "/operator", null, 409);
    assertEquals("RELEASED", internal("POST", winner + "/release", null).path("status").asText());
    assertEquals("RELEASED", internal("PUT", winner, input).path("status").asText());
  }

  @Test
  void acceptanceAndCancellationRace() throws Exception {
    var req = request();
    assertEquals("AWAITING_ACCEPTANCE", req.path("status").asText());
    String rid = id(req), aid = id(proposal(rid));
    call("ADMIN", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, key(), 403);
    call("DRIVER", "GET", "/api/v1/assignments/" + aid, null, 403);
    call("OPERATOR", "GET", "/api/v1/assistance-requests/" + rid, null, 200);
    String acceptKey = key();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var x =
          pool.submit(
              () ->
                  raw(
                      "OPERATOR",
                      "POST",
                      "/api/v1/assignments/" + aid + "/acceptance",
                      null,
                      acceptKey));
      var y =
          pool.submit(
              () ->
                  raw(
                      "DRIVER",
                      "POST",
                      "/api/v1/assistance-requests/" + rid + "/cancellations",
                      object("reason", "Concurrent test cancellation"),
                      key()));
      var a = x.get();
      var b = y.get();
      assertEquals(
          List.of(200, 409), java.util.stream.Stream.of(a.status(), b.status()).sorted().toList());
      var result = call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200);
      if (a.status() == 200) {
        assertEquals("ASSIGNED", result.path("status").asText());
        assertFalse(result.path("firstAssignedAt").isNull());
        assertEquals(
            "ACCEPTED",
            call(
                    "OPERATOR",
                    "POST",
                    "/api/v1/assignments/" + aid + "/acceptance",
                    null,
                    acceptKey,
                    200)
                .path("status")
                .asText());
      } else {
        assertEquals("CANCELLED", result.path("status").asText());
        assertFalse(result.path("resourceReleasePending").asBoolean());
      }
    }
  }

  @Test
  void acceptsDurablyAndBlocksAdministration() throws Exception {
    var req = request();
    String rid = id(req), aid = id(proposal(rid)), operation = key();
    call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, operation, 200);
    for (int i = 0;
        i < 35
            && !call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200)
                .path("status")
                .asText()
                .equals("ACCEPTED");
        i++) Thread.sleep(1000);
    assertEquals(
        "ACCEPTED",
        call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200).path("status").asText());
    var current = call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200);
    assertEquals("ASSIGNED", current.path("status").asText());
    assertFalse(current.path("firstAssignedAt").isNull());
    call(
        "DRIVER",
        "POST",
        "/api/v1/assistance-requests/" + rid + "/cancellations",
        object("reason", "Cancellation after acceptance"),
        key(),
        409);
    call("ADMIN", "PATCH", "/api/v1/tow-trucks/" + truck, object("maximumLoadKg", 2500), 409);
    call(
        "ADMIN",
        "PATCH",
        "/api/v1/operators/" + operator + "/registration-status",
        object("status", "INACTIVE"),
        409);
    call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, operation, 200);
    for (int i = 0;
        i < 35
            && !call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200)
                .path("status")
                .asText()
                .equals("ACCEPTED");
        i++) Thread.sleep(1000);
    assertEquals(
        "ACCEPTED",
        call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200).path("status").asText());
  }

  @Test
  void rejectionReleasesAndIsIdempotent() throws Exception {
    var req = request();
    String rid = id(req), aid = id(proposal(rid)), operation = key();
    var reason = object("reason", "Operator rejects test proposal");
    call(
        "OPERATOR",
        "POST",
        "/api/v1/assignments/" + aid + "/rejection",
        object("reason", "short"),
        operation,
        400);
    assertEquals(
        "REJECTED",
        call(
                "OPERATOR",
                "POST",
                "/api/v1/assignments/" + aid + "/rejection",
                reason,
                operation,
                200)
            .path("status")
            .asText());
    call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/rejection", reason, operation, 200);
    call(
        "OPERATOR",
        "POST",
        "/api/v1/assignments/" + aid + "/rejection",
        object("reason", "Changed response content"),
        operation,
        409);
    assertEquals("RELEASED", internal("GET", aid, null).path("status").asText());
    assertFalse(
        call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200)
            .path("resourceReleasePending")
            .asBoolean());
  }

  @Test
  void lostConfirmationRecoveredAfterRestart() throws Exception {
    String rid = id(request()), aid = id(proposal(rid)), operation = key();
    String fingerprint =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest("ACCEPT:".getBytes(StandardCharsets.UTF_8)));
    // Simulate the exact persisted intent at the crash boundary, before receiving Fleet's response.
    sql(
        "begin; update assignments set response_key='"
            + operation
            + "',response_kind='ACCEPT',response_fingerprint='"
            + fingerprint
            + "' where id='"
            + UUID.fromString(aid)
            + "'; insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at,next_attempt_at) values ('"
            + UUID.randomUUID()
            + "','"
            + UUID.fromString(aid)
            + "','CONFIRM','RETRYING',now(),now(),now()+interval '5 seconds'); commit;");
    var confirmed = internal("POST", aid + "/confirmation", null);
    assertEquals("CONFIRMED", confirmed.path("status").asText());
    assertEquals(
        "ACCEPTANCE_CONFIRMATION_PENDING",
        call(
                "DRIVER",
                "POST",
                "/api/v1/assistance-requests/" + rid + "/cancellations",
                object("reason", "Cancel while result uncertain"),
                key(),
                409)
            .path("code")
            .asText());
    Process p =
        new ProcessBuilder("docker", "compose", "restart", "assistance-service")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    p.getInputStream().readAllBytes();
    assertEquals(0, p.waitFor());
    boolean ready = false;
    for (int i = 0; i < 30; i++) {
      try {
        if (raw("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, null).status() == 200) {
          ready = true;
          break;
        }
      } catch (java.io.IOException ignored) {
      }
      Thread.sleep(1000);
    }
    assertTrue(ready);
    call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, operation, 200);
    for (int i = 0;
        i < 35
            && !call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200)
                .path("status")
                .asText()
                .equals("ACCEPTED");
        i++) Thread.sleep(1000);
    assertEquals(
        "ACCEPTED",
        call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200).path("status").asText());
    var result = call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200);
    assertEquals("ASSIGNED", result.path("status").asText());
    assertEquals(
        Instant.parse(confirmed.path("confirmedAt").asText()),
        Instant.parse(result.path("firstAssignedAt").asText()));
  }

  private static void sql(String statement) throws Exception {
    Process p =
        new ProcessBuilder(
                "docker",
                "compose",
                "exec",
                "-T",
                "postgres",
                "psql",
                "-U",
                "assistance_user",
                "-d",
                "assistance_db",
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                statement)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    p.getInputStream().readAllBytes();
    assertEquals(0, p.waitFor(), "Test fixture SQL");
  }

  static JsonNode internal(String method, String path, JsonNode body) throws Exception {
    String config =
        "url = \"http://fleet-service:8081/internal/reservations/"
            + path
            + "\"\nrequest = \""
            + method
            + "\"\nheader = \"Authorization: Bearer "
            + TOKENS.get("TECH")
            + "\"\nheader = \"Content-Type: application/json\"\n";
    if (body != null)
      config += "data = \"" + body.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
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
    assertEquals(200, Integer.parseInt(result.substring(split + 1)));
    return json(result.substring(0, split));
  }

  @AfterEach
  void cleanupFixtures() throws Exception {
    // Stop only these fixtures from participating in new searches before resolving their attempts.
    for (String id : requestIds) {
      UUID parsed = UUID.fromString(id);
      fixtureSql(
          "update assistance_requests set status=case when status in ('COMPLETED','CANCELLED') then status else 'UNFULFILLED' end,closed_at=coalesce(closed_at,now()),version=version+1,closure_reason_code=coalesce(closure_reason_code,'TEST_FIXTURE_CLEANUP') where id='"
              + parsed
              + "' and description='Phase 7 integration fixture'");
      String rows =
          fixtureSql(
              "select coalesce(json_agg(json_build_object('id',id,'payload',reserve_payload)), '[]'::json) from assignments where request_id='"
                  + parsed
                  + "'");
      for (var row : json(rows)) {
        String attempt = row.path("id").asText();
        internal("PUT", attempt, json(row.path("payload").asText()));
        internal("POST", attempt + "/release", null);
      }
      fixtureSql(
          "begin; update pending_actions set status='COMPLETED' where assignment_id in(select id from assignments where request_id='"
              + parsed
              + "'); update assignments set status='CLOSED',closed_at=coalesce(closed_at,now()) where request_id='"
              + parsed
              + "'; update assistance_requests set resource_release_pending=false where id='"
              + parsed
              + "'; commit;");
    }
    for (String id : reservationIds) internal("POST", id + "/release", null);
    if (truck != null) {
      call(
          "ADMIN",
          "PATCH",
          "/api/v1/tow-trucks/" + truck + "/operational-status",
          object("status", "INACTIVE", "reason", "Phase 7 fixture cleanup"),
          key(),
          200);
      call("ADMIN", "DELETE", "/api/v1/tow-trucks/" + truck + "/operator", null, 204);
    }
    if (operator != null)
      call(
          "OPERATOR",
          "PATCH",
          "/api/v1/operators/me/availability",
          object("status", "UNAVAILABLE"),
          200);
  }

  static String fixtureSql(String statement) throws Exception {
    Process p =
        new ProcessBuilder(
                "docker",
                "compose",
                "exec",
                "-T",
                "postgres",
                "psql",
                "-U",
                "assistance_user",
                "-d",
                "assistance_db",
                "-At",
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                statement)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, p.waitFor(), "Test fixture SQL");
    return result.strip();
  }

  @Test
  void activeProposalKeepsItsRouteEvidenceUntilAcceptance() throws Exception {
    String rid = id(request()), aid = id(proposal(rid));
    call(
        "OPERATOR",
        "PUT",
        "/api/v1/tow-trucks/assigned-to-me/location",
        object("latitude", -31.42, "longitude", -64.19),
        409);
    // Simulate the route-age boundary during an already published proposal, without waiting five
    // minutes.
    fixtureSql(
        "update candidate_evaluations set approach_route_obtained_at=now()-interval '301 seconds',transport_route_obtained_at=now()-interval '301 seconds' where id in(select candidate_evaluation_id from assignments where id='"
            + UUID.fromString(aid)
            + "'); update evaluations set route_obtained_at=now()-interval '301 seconds' where request_id='"
            + UUID.fromString(rid)
            + "'");
    String calls = fixtureSql("select count(*) from routing_calls");
    assertEquals(
        "ACCEPTED",
        call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, key(), 200)
            .path("status")
            .asText());
    assertEquals(
        "ASSIGNED",
        call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200)
            .path("status")
            .asText());
    assertEquals(
        calls,
        fixtureSql("select count(*) from routing_calls"),
        "Accepting an active proposal must not request another route");
  }
}
