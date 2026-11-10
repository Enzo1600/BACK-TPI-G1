package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.auxiruta.assistance.requests.DomainTypes.*;

@Service
public class EvaluationStore {
  public record Input(
      UUID requestId,
      long version,
      Status status,
      int attemptNumber,
      Instant deadline,
      String rules,
      BigDecimal originLat,
      BigDecimal originLon,
      BigDecimal destinationLat,
      BigDecimal destinationLon,
      String vehicleType,
      BigDecimal weight,
      String mobility) {}

  private final RequestRepository requests;
  private final EvaluationRepository evaluations;
  private final CandidateEvaluationRepository candidates;
  private final HistoryRepository history;
  private final Clock clock;

  public EvaluationStore(
      RequestRepository requests,
      EvaluationRepository evaluations,
      CandidateEvaluationRepository candidates,
      HistoryRepository history,
      Clock clock) {
    this.requests = requests;
    this.evaluations = evaluations;
    this.candidates = candidates;
    this.history = history;
    this.clock = clock;
  }

  @Transactional
  public Input load(UUID id) {
    var maybe = requests.lockById(id);
    if (maybe.isEmpty()) return null;
    var r = maybe.get();
    if (!Set.of(Status.REQUESTED, Status.PENDING_EVALUATION, Status.SEARCHING_ALTERNATIVE)
            .contains(r.status)
        || r.resourceReleasePending
        || r.evaluationLeaseUntil.isAfter(clock.instant())
        || !r.searchDeadline.isAfter(clock.instant())) return null;
    Instant lease = clock.instant().plusSeconds(120);
    r.evaluationLeaseUntil = lease;
    r.nextEvaluationAt = lease.isBefore(r.searchDeadline) ? lease : r.searchDeadline;
    requests.flush();
    return new Input(
        id,
        r.version,
        r.status,
        r.searchAttemptNumber,
        r.searchDeadline,
        r.configurationSnapshot,
        r.originLatitude,
        r.originLongitude,
        r.destinationLatitude,
        r.destinationLongitude,
        r.declaredVehicleType.name(),
        r.declaredWeightKg,
        r.mobilityCondition.name());
  }

  @Transactional
  public boolean save(Input input, Evaluation result, List<CandidateEvaluation> details) {
    var maybe = requests.lockById(input.requestId());
    if (maybe.isEmpty()) return false;
    var r = maybe.get();
    if (r.version != input.version()
        || r.status != input.status()
        || !r.searchDeadline.isAfter(clock.instant())) return false;
    r.evaluationLeaseUntil = Instant.EPOCH;
    result.request = r;
    result.evaluatedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var stored = evaluations.saveAndFlush(result);
    for (var c : details) {
      c.evaluation = stored;
      candidates.save(c);
    }
    candidates.flush();
    Status previous = r.status;
    r.status =
        "REJECTED".equals(result.result)
            ? (r.firstAssignedAt == null ? Status.REJECTED : Status.UNFULFILLED)
            : Status.PENDING_EVALUATION;
    r.updatedAt = result.evaluatedAt;
    try {
      int interval =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readTree(r.configurationSnapshot)
              .path("search")
              .path("evaluation-interval-seconds")
              .asInt();
      Instant next = result.evaluatedAt.plusSeconds(interval);
      r.nextEvaluationAt = next.isBefore(r.searchDeadline) ? next : r.searchDeadline;
    } catch (Exception e) {
      throw new IllegalStateException("Invalid rules");
    }
    if (r.status == Status.REJECTED || r.status == Status.UNFULFILLED) {
      r.closedAt = result.evaluatedAt;
      r.closureReasonCode = result.reasonCode;
    }
    RequestStatusHistory h = new RequestStatusHistory();
    h.request = r;
    h.previousStatus = previous;
    h.newStatus = r.status;
    h.actorType = "SYSTEM";
    h.reason = result.reasonCode;
    h.details = "evaluationId=" + stored.id + " result=" + result.result;
    h.occurredAt = result.evaluatedAt;
    history.saveAndFlush(h);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                org.slf4j.LoggerFactory.getLogger(EvaluationStore.class)
                    .info(
                        "Evaluation completed requestId={} result={} reason={}",
                        input.requestId(),
                        result.result,
                        result.reasonCode);
              }
            });
    return true;
  }
}
