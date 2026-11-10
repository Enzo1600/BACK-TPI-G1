package com.auxiruta.gateway.integration;

import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

@EnabledIfSystemProperty(named = "auxiruta.phase9", matches = "true")
class Phase9IntegrationTest {
  Phase7IntegrationTest fixture = new Phase7IntegrationTest();
  String rid;
  boolean fleetStopped;

  @BeforeAll
  static void initializeClient() throws Exception {
    initialize();
    assertEquals(
        "AR-MVP-5-DEMO",
        call("ADMIN", "GET", "/api/v1/configuration/assistance", null, 200)
            .path("rules-version")
            .asText(),
        "Run with SPRING_PROFILES_ACTIVE=docker,demo");
  }

  @BeforeEach
  void resources() throws Exception {
    fixture.resources();
  }

  JsonNode request() throws Exception {
    var r = fixture.request();
    rid = id(r);
    return r;
  }

  JsonNode current() throws Exception {
    return call("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, 200);
  }

  interface Check {
    boolean ready() throws Exception;
  }

  void await(Check check, int seconds) throws Exception {
    Instant deadline = Instant.now().plusSeconds(seconds);
    while (Instant.now().isBefore(deadline)) {
      if (check.ready()) return;
      Thread.sleep(250);
    }
    fail("Automatic condition did not occur within " + seconds + " seconds");
  }

  JsonNode active() throws Exception {
    for (int page = 0; page < 100; page++) {
      var list =
          call("OPERATOR", "GET", "/api/v1/operator/assignments?size=100&page=" + page, null, 200);
      for (var a : list.path("content"))
        if (a.path("requestId").asText().equals(rid)
            && a.path("status").asText().equals("PROPOSED")) return a;
      if (page + 1 >= list.path("totalPages").asInt()) break;
    }
    return null;
  }

  String proposal() throws Exception {
    await(() -> active() != null, 20);
    String id = id(active());
    fixture.reservationIds.add(id);
    return id;
  }

  void accept(String aid) throws Exception {
    call("OPERATOR", "POST", "/api/v1/assignments/" + aid + "/acceptance", null, key(), 200);
    await(() -> current().path("status").asText().equals("ASSIGNED"), 10);
  }

  void sql(String statement) throws Exception {
    Phase7IntegrationTest.fixtureSql(statement);
  }

  String uuid(String id) {
    return UUID.fromString(id).toString();
  }

  @Test
  void availabilityTriggersSearchAndOfferExpiryPreservesAttempt() throws Exception {
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "UNAVAILABLE"),
        200);
    var initial = request();
    String deadline = initial.path("searchDeadline").asText();
    assertEquals(
        60,
        Duration.between(Instant.parse(initial.path("createdAt").asText()), Instant.parse(deadline))
            .toSeconds());
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "AVAILABLE"),
        200);
    String aid = proposal();
    await(
        () ->
            call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200)
                .path("status")
                .asText()
                .equals("EXPIRED"),
        20);
    await(() -> !current().path("resourceReleasePending").asBoolean(), 10);
    assertEquals(1, current().path("searchAttemptNumber").asInt());
    assertEquals(deadline, current().path("searchDeadline").asText());
  }

  @Test
  void emptySearchClosesAutomatically() throws Exception {
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "UNAVAILABLE"),
        200);
    request();
    sql(
        "update assistance_requests set search_deadline=now()+interval '2 seconds',next_evaluation_at=now() where id='"
            + uuid(rid)
            + "'");
    await(() -> current().path("status").asText().equals("UNFULFILLED"), 10);
    assertEquals("NO_AVAILABLE_RESOURCE", current().path("closureReasonCode").asText());
  }

  @Test
  void twoReassignmentsThenCloseWithoutRestoringCancellation() throws Exception {
    request();
    String firstAssigned = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      String aid = proposal();
      accept(aid);
      var accepted = current();
      assertEquals(attempt, accepted.path("searchAttemptNumber").asInt());
      if (firstAssigned == null) firstAssigned = accepted.path("firstAssignedAt").asText();
      else assertEquals(firstAssigned, accepted.path("firstAssignedAt").asText());
      call(
          "DRIVER",
          "POST",
          "/api/v1/assistance-requests/" + rid + "/cancellations",
          object("reason", "Cannot cancel after first acceptance"),
          key(),
          409);
      sql(
          "update assignments set accepted_at=now()-interval '21 seconds' where id='"
              + uuid(aid)
              + "'; update assistance_requests set next_evaluation_at=now() where id='"
              + uuid(rid)
              + "'");
      if (attempt < 3) {
        int next = attempt + 1;
        await(() -> current().path("searchAttemptNumber").asInt() == next, 15);
      } else await(() -> current().path("status").asText().equals("UNFULFILLED"), 15);
    }
    assertEquals("REASSIGNMENT_LIMIT_REACHED", current().path("closureReasonCode").asText());
    assertFalse(current().path("resourceReleasePending").asBoolean());
  }

  @Test
  void confirmationIsRecoveredAfterRestartAndDeadline() throws Exception {
    request();
    String aid = proposal(), operation = key();
    String fingerprint =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest("ACCEPT:".getBytes(StandardCharsets.UTF_8)));
    sql(
        "begin; update assignments set response_key='"
            + operation
            + "',response_kind='ACCEPT',response_fingerprint='"
            + fingerprint
            + "' where id='"
            + uuid(aid)
            + "'; insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at,next_attempt_at) values ('"
            + key()
            + "','"
            + uuid(aid)
            + "','CONFIRM','RETRYING',now(),now(),now()+interval '8 seconds'); commit;");
    var confirmed = Phase7IntegrationTest.internal("POST", aid + "/confirmation", null);
    assertEquals("CONFIRMED", confirmed.path("status").asText());
    sql(
        "update assistance_requests set search_deadline=now(),next_evaluation_at=now() where id='"
            + uuid(rid)
            + "'");
    call(
        "DRIVER",
        "POST",
        "/api/v1/assistance-requests/" + rid + "/cancellations",
        object("reason", "Confirmation result is uncertain"),
        key(),
        409);
    Phase8IntegrationTest.container("restart", "assistance-service");
    await(
        () -> {
          var response = raw("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, null);
          return response.status() == 200
              && response.body().path("status").asText().equals("ASSIGNED");
        },
        18);
    assertEquals(
        Instant.parse(confirmed.path("confirmedAt").asText()),
        Instant.parse(current().path("firstAssignedAt").asText()));
  }

  @Test
  void cancelledUncertainReserveIsCompensatedAutomatically() throws Exception {
    request();
    String aid = proposal();
    sql(
        "begin; update assignments set status='RESERVING' where id='"
            + uuid(aid)
            + "'; update pending_actions set status='RETRYING',next_attempt_at=now()+interval '8 seconds' where assignment_id='"
            + uuid(aid)
            + "' and kind='RESERVE'; update assistance_requests set status='PENDING_EVALUATION',next_evaluation_at=now() where id='"
            + uuid(rid)
            + "'; commit;");
    call(
        "DRIVER",
        "POST",
        "/api/v1/assistance-requests/" + rid + "/cancellations",
        object("reason", "Cancel before uncertain hold resolves"),
        key(),
        200);
    assertTrue(current().path("resourceReleasePending").asBoolean());
    Phase8IntegrationTest.container("restart", "assistance-service");
    await(
        () -> {
          var response = raw("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, null);
          return response.status() == 200
              && !response.body().path("resourceReleasePending").asBoolean();
        },
        25);
    assertEquals("CANCELLED", current().path("status").asText());
    assertTrue(
        List.of("RELEASED", "EXPIRED")
            .contains(Phase7IntegrationTest.internal("GET", aid, null).path("status").asText()));
  }

  @Test
  void releaseSurvivesRestartWithoutRepeatingCompletion() throws Exception {
    request();
    String aid = proposal();
    accept(aid);
    for (String step : List.of("dispatch", "arrival", "loading", "transport-start"))
      call("OPERATOR", "POST", "/api/v1/assistance-requests/" + rid + "/" + step, null, key(), 200);
    Phase8IntegrationTest.container("stop", "fleet-service");
    fleetStopped = true;
    call(
        "OPERATOR",
        "POST",
        "/api/v1/assistance-requests/" + rid + "/completion",
        object("note", "Delivery finished during dependency outage"),
        key(),
        200);
    assertTrue(current().path("resourceReleasePending").asBoolean());
    Phase8IntegrationTest.container("restart", "assistance-service");
    startFleet();
    await(
        () -> {
          var response = raw("DRIVER", "GET", "/api/v1/assistance-requests/" + rid, null, null);
          return response.status() == 200
              && !response.body().path("resourceReleasePending").asBoolean();
        },
        50);
    assertEquals("COMPLETED", current().path("status").asText());
    fixture.requestIds.remove(rid);
  }

  @Test
  void fiveExpiredOffersExhaustTheSearchWithoutChangingDeadline() throws Exception {
    var r = request();
    String deadline = r.path("searchDeadline").asText();
    for (int i = 0; i < 5; i++) {
      String aid = proposal();
      sql(
          "update assignments set expires_at=now() where id='"
              + uuid(aid)
              + "' ; update assistance_requests set next_evaluation_at=now() where id='"
              + uuid(rid)
              + "'");
      await(
          () ->
              call("OPERATOR", "GET", "/api/v1/assignments/" + aid, null, 200)
                  .path("status")
                  .asText()
                  .equals("EXPIRED"),
          10);
      await(() -> !current().path("resourceReleasePending").asBoolean(), 10);
      if (i < 4) {
        call(
            "ADMIN",
            "PATCH",
            "/api/v1/tow-trucks/" + fixture.truck + "/operational-status",
            object("status", "INACTIVE", "reason", "Retire previous test candidate"),
            key(),
            200);
        call("ADMIN", "DELETE", "/api/v1/tow-trucks/" + fixture.truck + "/operator", null, 204);
        fixture.truck =
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
            "/api/v1/tow-trucks/" + fixture.truck + "/operator",
            object("operatorId", fixture.operator),
            200);
        call(
            "ADMIN",
            "PATCH",
            "/api/v1/tow-trucks/" + fixture.truck + "/operational-status",
            object("status", "ACTIVE", "reason", "Prepare another test candidate"),
            key(),
            200);
        call(
            "OPERATOR",
            "PUT",
            "/api/v1/tow-trucks/assigned-to-me/location",
            object("latitude", -31.4167, "longitude", -64.1833),
            200);
      }
    }
    await(() -> current().path("status").asText().equals("UNFULFILLED"), 10);
    assertEquals("OFFER_LIMIT_REACHED", current().path("closureReasonCode").asText());
    assertEquals(deadline, current().path("searchDeadline").asText());
  }

  void startFleet() throws Exception {
    Phase8IntegrationTest.container("start", "fleet-service");
    await(
        () -> {
          var response = raw("OPERATOR", "GET", "/api/v1/tow-trucks/assigned-to-me", null, null);
          return response.status() == 200;
        },
        30);
    fleetStopped = false;
  }

  @AfterEach
  void cleanup() throws Exception {
    if (fleetStopped) startFleet();
    fixture.cleanupFixtures();
  }

  @Test
  void rejectingOperatorIsExcludedInNextAttempt() throws Exception {
    request();
    String aid = proposal();
    call(
        "OPERATOR",
        "POST",
        "/api/v1/assignments/" + aid + "/rejection",
        object("reason", "Declino esta solicitud de asistencia"),
        key(),
        200);
    await(() -> !current().path("resourceReleasePending").asBoolean(), 10);
    // Controlled fixture: advance the search to attempt 2, keeping the real rejection history.
    sql(
        "update assistance_requests set search_attempt_number=2,status='PENDING_EVALUATION',next_evaluation_at=now(),search_deadline=now()+interval '60 seconds' where id='"
            + uuid(rid)
            + "'");
    await(
        () ->
            Integer.parseInt(
                    Phase7IntegrationTest.fixtureSql(
                        "select count(*) from evaluations where request_id='"
                            + uuid(rid)
                            + "' and search_attempt_number=2"))
                > 0,
        15);
    Thread.sleep(3000);
    assertNull(active());
    assertEquals(
        "0",
        Phase7IntegrationTest.fixtureSql(
            "select count(*) from assignments where request_id='"
                + uuid(rid)
                + "' and search_attempt_number=2"));
  }
}
