package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvaluationReadService {
  public record CandidateView(
      UUID towTruckId,
      boolean eligible,
      String reasonCode,
      BigDecimal approachDistanceM,
      Integer adjustedApproachSeconds,
      Instant estimatedServiceEndAt,
      Integer rankingPosition) {}

  public record EvaluationView(
      UUID id,
      String result,
      String reasonCode,
      Instant evaluatedAt,
      BigDecimal transportDistanceM,
      Integer adjustedTransportSeconds,
      List<CandidateView> candidates) {}

  private final EvaluationRepository evaluations;
  private final CandidateEvaluationRepository candidates;

  public EvaluationReadService(
      EvaluationRepository evaluations, CandidateEvaluationRepository candidates) {
    this.evaluations = evaluations;
    this.candidates = candidates;
  }

  public EvaluationView latest(UUID id) {
    return evaluations
        .findFirstByRequestIdOrderByEvaluatedAtDescIdDesc(id)
        .map(
            e ->
                new EvaluationView(
                    e.id,
                    e.result,
                    e.reasonCode,
                    e.evaluatedAt,
                    e.transportDistanceM,
                    e.adjustedTransportSeconds,
                    candidates.findByEvaluationId(e.id).stream()
                        .sorted(
                            Comparator.comparing(
                                    (CandidateEvaluation c) -> c.rankingPosition,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(c -> c.towTruckId.toString()))
                        .map(
                            c ->
                                new CandidateView(
                                    c.towTruckId,
                                    c.eligible,
                                    c.reasonCode,
                                    c.approachDistanceM,
                                    c.adjustedApproachSeconds,
                                    c.estimatedServiceEndAt,
                                    c.rankingPosition))
                        .toList()))
        .orElse(null);
  }
}
