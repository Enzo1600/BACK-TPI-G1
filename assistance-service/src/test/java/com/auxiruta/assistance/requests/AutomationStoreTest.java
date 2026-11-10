package com.auxiruta.assistance.requests;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;

class AutomationStoreTest {
  JdbcTemplate jdbc;
  RequestRepository requests;
  EvaluationRepository evaluations;
  HistoryRepository history;
  AutomationStore store;
  AssistanceRequest request;
  Instant now = Instant.parse("2026-11-08T12:00:00Z");
  Clock clock;
  UUID assignment = UUID.randomUUID();

  @BeforeEach
  void setup() throws Exception {
    jdbc = mock(JdbcTemplate.class);
    requests = mock(RequestRepository.class);
    evaluations = mock(EvaluationRepository.class);
    history = mock(HistoryRepository.class);
    clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(i -> now);
    ObjectMapper mapper = new ObjectMapper();
    store = new AutomationStore(jdbc, requests, history, evaluations, mapper, clock);
    request = new AssistanceRequest();
    request.configurationSnapshot = new RulesConfiguration(mapper).snapshot();
    request.createdAt = now.minusSeconds(10);
    request.searchDeadline = now.plusSeconds(600);
    request.status = Status.PENDING_EVALUATION;
    when(requests.vehicleId(request.id)).thenReturn(Optional.of(UUID.randomUUID()));
    when(requests.lockById(request.id)).thenReturn(Optional.of(request));
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void finish() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  void pendingConfirmationPreventsDeadlineClosure() {
    request.searchDeadline = now;
    when(jdbc.queryForObject(contains("p.kind=?"), eq(Long.class), eq(request.id), eq("CONFIRM")))
        .thenReturn(1L);
    assertNull(store.advance(request.id));
    assertNull(request.closedAt);
    assertEquals(Status.PENDING_EVALUATION, request.status);
  }

  @Test
  void deadlineClosesWithoutExtendingSearch() {
    request.searchDeadline = now;
    var e = new Evaluation();
    e.result = "SEARCHING";
    e.reasonCode = "NO_AVAILABLE_RESOURCE";
    when(evaluations.findFirstByRequestIdOrderByEvaluatedAtDescIdDesc(request.id))
        .thenReturn(Optional.of(e));
    assertNull(store.advance(request.id));
    assertEquals(Status.UNFULFILLED, request.status);
    assertEquals("NO_AVAILABLE_RESOURCE", request.closureReasonCode);
    assertEquals(now, request.searchDeadline);
  }

  @Test
  void dispatchExpiryWaitsForReleaseBeforeStartingNewDeadline() {
    request.status = Status.ASSIGNED;
    request.firstAssignedAt = now.minusSeconds(300);
    when(jdbc.queryForList(contains("accepted_at"), eq(request.id)))
        .thenReturn(
            List.of(
                Map.of("id", assignment, "accepted_at", Timestamp.from(now.minusSeconds(300)))));
    Instant oldDeadline = request.searchDeadline;
    store.advance(request.id);
    assertTrue(request.replacementPending);
    assertTrue(request.resourceReleasePending);
    assertEquals(Status.SEARCHING_ALTERNATIVE, request.status);
    assertEquals(1, request.searchAttemptNumber);
    assertEquals(oldDeadline, request.searchDeadline);
    now = now.plusSeconds(90);
    request.resourceReleasePending = false;
    var work = store.advance(request.id);
    assertTrue(work.evaluate());
    assertEquals(2, request.searchAttemptNumber);
    assertEquals(now.plusSeconds(600), request.searchDeadline);
    assertFalse(request.replacementPending);
    assertNotNull(request.firstAssignedAt);
  }

  @Test
  void expiredOfferDuringReplacementDoesNotConsumeAnotherReassignment() {
    request.status = Status.AWAITING_ACCEPTANCE;
    request.searchAttemptNumber = 2;
    request.firstAssignedAt = now.minusSeconds(100);
    when(jdbc.queryForList(contains("select id,expires_at"), eq(request.id)))
        .thenReturn(List.of(Map.of("id", assignment, "expires_at", Timestamp.from(now))));
    store.advance(request.id);
    assertFalse(request.replacementPending);
    assertEquals(2, request.searchAttemptNumber);
    assertTrue(request.resourceReleasePending);
  }

  @Test
  void thirdAcceptedAttemptCannotStartFourthSearch() {
    request.status = Status.SEARCHING_ALTERNATIVE;
    request.firstAssignedAt = now.minusSeconds(100);
    request.searchAttemptNumber = 3;
    request.replacementPending = true;
    store.advance(request.id);
    assertEquals(Status.UNFULFILLED, request.status);
    assertEquals("REASSIGNMENT_LIMIT_REACHED", request.closureReasonCode);
    assertEquals(3, request.searchAttemptNumber);
  }

  @Test
  void fiveCreatedOffersCloseTheAttempt() {
    when(jdbc.queryForObject(
            contains("expires_at is not null"), eq(Long.class), eq(request.id), eq(1)))
        .thenReturn(5L);
    store.advance(request.id);
    assertEquals("OFFER_LIMIT_REACHED", request.closureReasonCode);
  }

  @Test
  void executionStatesAreNeverAutomaticallyReassigned() {
    for (Status status :
        List.of(Status.DISPATCHED, Status.ARRIVED, Status.VEHICLE_LOADED, Status.IN_TRANSIT)) {
      request.status = status;
      request.searchDeadline = now.minusSeconds(1000);
      assertNull(store.advance(request.id));
      assertEquals(status, request.status);
      assertNull(request.closedAt);
    }
    verifyNoInteractions(history);
  }
}
