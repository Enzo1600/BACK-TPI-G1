package com.auxiruta.fleet.catalog;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
import static com.auxiruta.fleet.catalog.FleetDtos.*;

class FleetServiceTest {
  @BeforeEach
  void reservations() {
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "reservations", mock(ReservationRepository.class));
  }

  final OperatorRepository operators = mock(OperatorRepository.class);
  final TowTruckRepository trucks = mock(TowTruckRepository.class);
  final TruckStatusHistoryRepository history = mock(TruckStatusHistoryRepository.class);
  final Instant instant = Instant.parse("2026-11-07T12:00:00.123456789Z");
  final FleetService service =
      new FleetService(operators, trucks, history, Clock.fixed(instant, ZoneOffset.UTC));

  @Test
  void repeatedAvailabilityDoesNotResetFreeTime() {
    Operator o = new Operator();
    o.keycloakSubject = "subject";
    o.availabilityStatus = Availability.AVAILABLE;
    o.availableSince = instant.minusSeconds(100);
    when(operators.findByKeycloakSubject("subject")).thenReturn(Optional.of(o));
    when(operators.lockBySubject("subject")).thenReturn(Optional.of(o));
    assertEquals(
        o.availableSince,
        service
            .availability("subject", new AvailabilityChange(Availability.AVAILABLE))
            .availableSince());
  }

  @Test
  void invalidQualificationDoesNotWrite() {
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.qualification(
                UUID.randomUUID(),
                new QualificationChange(Qualification.LIGHT_TOWING, instant, instant)));
    verifyNoInteractions(operators);
  }

  @Test
  void locationTimestampComesFromClockWithDatabasePrecision() {
    Operator o = new Operator();
    o.keycloakSubject = "subject";
    TowTruck t = new TowTruck();
    t.operator = o;
    when(operators.findByKeycloakSubject("subject")).thenReturn(Optional.of(o));
    when(trucks.assignedId("subject")).thenReturn(Optional.of(t.id));
    when(trucks.lockById(t.id)).thenReturn(Optional.of(t));
    assertEquals(
        Instant.parse("2026-11-07T12:00:00.123456Z"),
        service
            .location("subject", new Location(BigDecimal.ZERO, BigDecimal.ZERO))
            .locationUpdatedAt());
  }

  @Test
  void missingOwnProfileIsHidden() {
    when(operators.findByKeycloakSubject("other")).thenReturn(Optional.empty());
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.me("other"))
            .getStatusCode()
            .value());
  }

  @Test
  void replayReturnsOriginalEventWithoutReapplyingOldState() {
    TowTruck t = new TowTruck();
    t.operationalStatus = Operational.MAINTENANCE;
    TruckStatusHistory h = new TruckStatusHistory();
    h.truck = t;
    h.previousStatus = Operational.INACTIVE;
    h.newStatus = Operational.ACTIVE;
    h.reason = "Reason";
    h.changedBySubject = "admin";
    h.operationKey = "key";
    h.changedAt = instant;
    when(trucks.lockById(t.id)).thenReturn(Optional.of(t));
    when(history.findByOperationKey("key")).thenReturn(Optional.of(h));
    assertEquals(
        Operational.ACTIVE,
        service
            .status(t.id, new StatusChange(Operational.ACTIVE, "Reason"), "admin", "key")
            .newStatus());
    assertEquals(Operational.MAINTENANCE, t.operationalStatus);
    verify(history, never()).save(any());
  }

  @Test
  void committedTruckCannotChangeLocationButReleasedTruckCan() {
    Operator o = new Operator();
    o.keycloakSubject = "subject";
    TowTruck t = new TowTruck();
    t.operator = o;
    t.latitude = BigDecimal.ONE;
    t.longitude = BigDecimal.ONE;
    t.locationUpdatedAt = instant.minusSeconds(301);
    when(trucks.assignedId("subject")).thenReturn(Optional.of(t.id));
    when(trucks.lockById(t.id)).thenReturn(Optional.of(t));
    ReservationRepository reservations =
        (ReservationRepository)
            org.springframework.test.util.ReflectionTestUtils.getField(service, "reservations");
    when(reservations.busy(t.id, null)).thenReturn(true);
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.location("subject", new Location(BigDecimal.ZERO, BigDecimal.ZERO)))
            .getStatusCode()
            .value());
    assertEquals(BigDecimal.ONE, t.latitude);
    assertEquals(instant.minusSeconds(301), t.locationUpdatedAt);
    verify(trucks, never()).flush();
    when(reservations.busy(t.id, null)).thenReturn(false);
    assertEquals(
        BigDecimal.ZERO,
        service.location("subject", new Location(BigDecimal.ZERO, BigDecimal.ZERO)).latitude());
  }
}
