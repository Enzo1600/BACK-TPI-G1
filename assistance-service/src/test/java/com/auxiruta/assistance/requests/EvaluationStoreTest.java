package com.auxiruta.assistance.requests;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationStoreTest {
  @Test
  void concurrentCancellationCannotBeOverwritten() {
    var requests = mock(RequestRepository.class);
    var evaluations = mock(EvaluationRepository.class);
    var candidates = mock(CandidateEvaluationRepository.class);
    var histories = mock(HistoryRepository.class);
    var now = Instant.parse("2026-11-08T00:00:00Z");
    var store =
        new EvaluationStore(
            requests, evaluations, candidates, histories, Clock.fixed(now, ZoneOffset.UTC));
    AssistanceRequest r = new AssistanceRequest();
    r.status = DomainTypes.Status.CANCELLED;
    r.version = 1;
    r.searchDeadline = now.plusSeconds(600);
    when(requests.lockById(r.id)).thenReturn(Optional.of(r));
    var input =
        new EvaluationStore.Input(
            r.id,
            0,
            DomainTypes.Status.REQUESTED,
            1,
            r.searchDeadline,
            "{}",
            null,
            null,
            null,
            null,
            "CAR",
            null,
            "ROLLABLE");
    assertFalse(store.save(input, new Evaluation(), List.of()));
    verifyNoInteractions(evaluations, candidates, histories);
  }
}
