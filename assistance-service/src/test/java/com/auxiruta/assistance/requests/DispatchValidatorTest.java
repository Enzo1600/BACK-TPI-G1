package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.auxiruta.assistance.routing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.server.ResponseStatusException;

class DispatchValidatorTest {
  Instant now = Instant.parse("2026-11-08T12:00:00Z");
  FleetClient fleet = mock(FleetClient.class);
  RouteProvider routes = mock(RouteProvider.class);
  ObjectMapper mapper = new ObjectMapper();
  ExecutionStore.Input input;
  DispatchValidator validator;

  @BeforeEach
  void setup() throws Exception {
    var rules = new RulesConfiguration(mapper);
    input =
        new ExecutionStore.Input(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "operator",
            now.minusSeconds(1),
            rules.snapshot(),
            new BigDecimal("-31.4167"),
            new BigDecimal("-64.1833"),
            new BigDecimal("-31.42"),
            new BigDecimal("-64.19"),
            "ROLLABLE");
    validator = new DispatchValidator(fleet, routes, mapper, Clock.fixed(now, ZoneOffset.UTC));
    resource(now, now.plusSeconds(7200), true);
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenReturn(new RouteProvider.Route(new BigDecimal("1736.6"), new BigDecimal("100"), now));
  }

  void resource(Instant location, Instant qualification, boolean eligible) {
    when(fleet.eligibility(input.assignmentId()))
        .thenReturn(
            new FleetClient.Eligibility(
                input.assignmentId(),
                input.requestId(),
                input.truckId(),
                input.operatorId(),
                input.subject(),
                eligible,
                "TEST",
                input.originLat(),
                input.originLon(),
                location,
                qualification,
                now));
  }

  @Test
  void validResourceProducesAuditableEvidence() {
    var e = validator.validate(input);
    assertTrue(e.json().contains("1736.6"));
    assertEquals(2525, e.plannedSeconds());
  }

  @Test
  void acceptedLocationDoesNotExpireForDispatch() {
    resource(now.minusSeconds(900), now.plusSeconds(7200), true);
    assertNotNull(validator.validate(input));
  }

  @Test
  void qualificationMustOutlivePlannedEndStrictly() {
    resource(now, now.plusSeconds(2525), true);
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> validator.validate(input))
            .getStatusCode()
            .value());
  }

  @Test
  void unconfirmedResourceCannotDispatch() {
    resource(now, now.plusSeconds(7200), false);
    assertThrows(ResponseStatusException.class, () -> validator.validate(input));
    verifyNoInteractions(routes);
  }

  @Test
  void quotaIsTemporaryFailure() {
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new RoutingFailure(RoutingFailure.Reason.RATE_LIMIT));
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, () -> validator.validate(input))
            .getStatusCode()
            .value());
  }

  @Test
  void confirmedMissingRouteIsDomainConflict() {
    when(routes.route(any(), any(), anyString(), anyInt(), anyInt()))
        .thenThrow(new RoutingFailure(RoutingFailure.Reason.NO_ROUTE));
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> validator.validate(input))
            .getStatusCode()
            .value());
  }

  @Test
  void malformedFleetResponseIsTechnicalFailure() {
    when(fleet.eligibility(input.assignmentId())).thenReturn(null);
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, () -> validator.validate(input))
            .getStatusCode()
            .value());
    verifyNoInteractions(routes);
  }
}
