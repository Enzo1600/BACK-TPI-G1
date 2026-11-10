package com.auxiruta.assistance.requests;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.time.*;
import java.math.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.auxiruta.assistance.routing.*;
import static com.auxiruta.assistance.routing.RouteProvider.*;

class EvaluationServiceTest {
  EvaluationStore store;
  RouteProvider routes;
  FleetClient fleet;
  EvaluationService service;
  EvaluationStore.Input input;
  Instant now = Instant.parse("2026-11-08T00:00:00Z");
  ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setup() throws Exception {
    store = mock(EvaluationStore.class);
    routes = mock(RouteProvider.class);
    fleet = mock(FleetClient.class);
    input =
        new EvaluationStore.Input(
            UUID.randomUUID(),
            0,
            DomainTypes.Status.REQUESTED,
            1,
            now.plusSeconds(600),
            new RulesConfiguration(mapper).snapshot(),
            new BigDecimal("-31.4167"),
            new BigDecimal("-64.1833"),
            new BigDecimal("-31.42"),
            new BigDecimal("-64.19"),
            "CAR",
            new BigDecimal("1000"),
            "ROLLABLE");
    when(store.load(input.requestId())).thenReturn(input);
    when(fleet.candidates(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of());
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenReturn(new Route(new BigDecimal("1000"), new BigDecimal("100"), now));
    service = new EvaluationService(store, routes, fleet, Clock.fixed(now, ZoneOffset.UTC), mapper);
  }

  Evaluation result() {
    var captor = ArgumentCaptor.forClass(Evaluation.class);
    verify(store).save(eq(input), captor.capture(), anyList());
    return captor.getValue();
  }

  FleetClient.Candidate candidate(String uuid, Instant free, Instant until) {
    return new FleetClient.Candidate(
        UUID.fromString(uuid),
        UUID.randomUUID(),
        "operator",
        new BigDecimal("3000"),
        Set.of("CAR"),
        true,
        "LIGHT_TOWING",
        now.minusSeconds(1000),
        until,
        free,
        new BigDecimal("-31.417"),
        new BigDecimal("-64.184"),
        now,
        100);
  }

  @Test
  void distanceLimitsAreInclusiveAndNotRounded() {
    for (String distance : List.of("500", "30000", "499.9999", "30000.0001")) {
      clearInvocations(store);
      when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
          .thenReturn(new Route(new BigDecimal(distance), new BigDecimal("100"), now));
      service.evaluate(input.requestId());
      assertEquals(
          distance.equals("500") || distance.equals("30000") ? "SEARCHING" : "REJECTED",
          result().result);
    }
  }

  @Test
  void adjustedSecondsRoundUp() {
    assertEquals(126, EvaluationService.adjusted(new BigDecimal("100.1"), new BigDecimal("1.25")));
  }

  @Test
  void transportDurationBoundary() {
    for (String duration : List.of("4320", "4320.0001")) {
      clearInvocations(store);
      when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
          .thenReturn(new Route(new BigDecimal("1000"), new BigDecimal(duration), now));
      service.evaluate(input.requestId());
      assertEquals(duration.equals("4320") ? "SEARCHING" : "REJECTED", result().result);
    }
  }

  @Test
  void coverageFailureAvoidsExternalCalls() {
    input =
        new EvaluationStore.Input(
            input.requestId(),
            0,
            input.status(),
            1,
            input.deadline(),
            input.rules(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            input.destinationLat(),
            input.destinationLon(),
            "CAR",
            input.weight(),
            "ROLLABLE");
    when(store.load(input.requestId())).thenReturn(input);
    service.evaluate(input.requestId());
    assertEquals("OUTSIDE_COVERAGE", result().reasonCode);
    verifyNoInteractions(routes, fleet);
  }

  @Test
  void dependencyFailureDoesNotReject() {
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new RoutingFailure(RoutingFailure.Reason.RATE_LIMIT));
    service.evaluate(input.requestId());
    assertEquals("PENDING_DEPENDENCY", result().result);
  }

  @Test
  void missingRouteIsARecordedDomainRejection() {
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new RoutingFailure(RoutingFailure.Reason.NO_ROUTE));
    service.evaluate(input.requestId());
    assertEquals("REJECTED", result().result);
  }

  @Test
  void rankingUsesDurationThenFreeTimeThenLexicalUuid() {
    var first =
        candidate(
            "00000000-0000-0000-0000-000000000001", now.minusSeconds(200), now.plusSeconds(10000));
    var second =
        candidate(
            "00000000-0000-0000-0000-000000000002", now.minusSeconds(200), now.plusSeconds(10000));
    var third =
        candidate(
            "00000000-0000-0000-0000-000000000003", now.minusSeconds(100), now.plusSeconds(10000));
    when(fleet.candidates(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(List.of(third, second, first));
    service.evaluate(input.requestId());
    assertEquals("VIABLE", result().result);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CandidateEvaluation>> rows = ArgumentCaptor.forClass(List.class);
    verify(store).save(eq(input), any(), rows.capture());
    assertEquals(
        1,
        rows.getValue().stream()
            .filter(c -> c.towTruckId.equals(first.towTruckId()))
            .findFirst()
            .orElseThrow()
            .rankingPosition);
    assertEquals(3, rows.getValue().get(0).rankingPosition);
  }

  @Test
  void qualificationMustExtendStrictlyBeyondPlannedEnd() {
    // 60 acceptance + 300 departure + 125 approach + 900 loading + 125 transport + 600 unloading +
    // 600 contingency.
    var resource =
        candidate(
            "00000000-0000-0000-0000-000000000001", now.minusSeconds(100), now.plusSeconds(2710));
    when(fleet.candidates(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(resource));
    service.evaluate(input.requestId());
    assertEquals("SEARCHING", result().result);
  }

  @Test
  void expiredOrClosedRequestIsNotEvaluated() {
    when(store.load(input.requestId())).thenReturn(null);
    service.evaluate(input.requestId());
    verifyNoInteractions(routes, fleet);
    verify(store, never()).save(any(), any(), any());
  }

  @Test
  void cancellationRaceDiscardsResultAtCommit() {
    when(store.save(any(), any(), any())).thenReturn(false);
    service.evaluate(input.requestId());
    verify(store).save(eq(input), any(), anyList());
  }
}
