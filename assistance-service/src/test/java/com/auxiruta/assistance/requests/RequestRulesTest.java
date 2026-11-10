package com.auxiruta.assistance.requests;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;
import static com.auxiruta.assistance.requests.RequestDtos.*;

class RequestRulesTest {
  private CreateRequest request(BigDecimal latitude, String description) {
    return new CreateRequest(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        latitude,
        new BigDecimal("-64.1833"),
        new BigDecimal("-31.42"),
        new BigDecimal("-64.19"),
        Mobility.ROLLABLE,
        description);
  }

  @Test
  void fingerprintNormalizesDecimalScale() {
    assertEquals(
        RequestService.fingerprint(request(new BigDecimal("-31.4167"), "Help")),
        RequestService.fingerprint(request(new BigDecimal("-31.416700"), "Help")));
  }

  @Test
  void changedBodyHasDifferentFingerprint() {
    assertNotEquals(
        RequestService.fingerprint(request(new BigDecimal("-31.4167"), "Help")),
        RequestService.fingerprint(request(new BigDecimal("-31.4167"), "Changed")));
  }

  @Test
  void previousAcceptanceBlocksCancellationEvenDuringAlternativeSearch() {
    AssistanceRequest r = new AssistanceRequest();
    r.status = Status.SEARCHING_ALTERNATIVE;
    assertTrue(RequestService.cancellable(r));
    r.firstAssignedAt = Instant.EPOCH;
    assertFalse(RequestService.cancellable(r));
  }

  @Test
  void terminalAndExecutionStatesCannotBeCancelled() {
    AssistanceRequest r = new AssistanceRequest();
    for (Status status :
        List.of(
            Status.ASSIGNED,
            Status.DISPATCHED,
            Status.ARRIVED,
            Status.VEHICLE_LOADED,
            Status.IN_TRANSIT,
            Status.COMPLETED,
            Status.REJECTED,
            Status.UNFULFILLED,
            Status.CANCELLED)) {
      r.status = status;
      assertFalse(RequestService.cancellable(r), status.name());
    }
  }

  @Test
  void onlyPreAcceptanceStatesCanBeCancelled() {
    AssistanceRequest r = new AssistanceRequest();
    for (Status status :
        List.of(
            Status.REQUESTED,
            Status.PENDING_EVALUATION,
            Status.AWAITING_ACCEPTANCE,
            Status.SEARCHING_ALTERNATIVE)) {
      r.status = status;
      assertTrue(RequestService.cancellable(r));
    }
  }

  @Test
  void plateNormalizedAndInvalidRejected() {
    assertEquals("AB123CD", RequestService.plate("ab-123 cd"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> RequestService.plate("!!!"));
  }

  @Test
  void snapshotIncludesAllParametersAndCorrectDeadline() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var rules = new RulesConfiguration(mapper);
    var snapshot = mapper.readTree(rules.snapshot());
    assertEquals(600, rules.searchSeconds());
    assertEquals("AR-MVP-5", rules.revision());
    assertEquals(2, snapshot.path("search").path("max-reassignments").asInt());
    assertEquals(1.25, snapshot.path("routing").path("duration-factor").asDouble());
    assertEquals(15000, snapshot.path("coverage").path("radius-meters").asInt());
  }

  @Test
  void creationUsesInjectedClockAndCopiesVehicleData() throws Exception {
    var vehicles = mock(VehicleRepository.class);
    var requests = mock(RequestRepository.class);
    var histories = mock(HistoryRepository.class);
    var rules = new RulesConfiguration(new com.fasterxml.jackson.databind.ObjectMapper());
    var instant = Instant.parse("2026-11-07T12:00:00Z");
    var service =
        new RequestService(
            vehicles,
            requests,
            histories,
            rules,
            Clock.fixed(instant, ZoneOffset.UTC),
            mock(EvaluationReadService.class));
    var input = request(new BigDecimal("-31.4167"), "Help");
    Vehicle v = new Vehicle();
    v.id = input.vehicleId();
    v.ownerSubject = "driver";
    v.vehicleType = VehicleType.SUV;
    v.declaredWeightKg = new BigDecimal("1200");
    when(vehicles.lockOwned(v.id, "driver")).thenReturn(Optional.of(v));
    when(requests.findByDriverSubjectAndIdempotencyKey("driver", "key"))
        .thenReturn(Optional.empty());
    when(requests.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
    try {
      var created = service.createRequest("driver", "key", input);
      assertTrue(created.created());
      assertEquals(instant.plusSeconds(600), created.request().searchDeadline());
      assertEquals(VehicleType.SUV, created.request().declaredVehicleType());
      v.declaredWeightKg = new BigDecimal("2000");
      assertEquals(new BigDecimal("1200"), created.request().declaredWeightKg());
      verify(histories).saveAndFlush(any());
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .clearSynchronization();
    }
  }
}
