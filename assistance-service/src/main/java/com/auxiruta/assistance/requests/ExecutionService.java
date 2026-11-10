package com.auxiruta.assistance.requests;

import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.auxiruta.assistance.requests.ExecutionStore.Step;

@Service
public class ExecutionService {
  private final ExecutionStore store;
  private final DispatchValidator dispatch;
  private final AssignmentCoordinator assignments;

  public ExecutionService(
      ExecutionStore store, DispatchValidator dispatch, AssignmentCoordinator assignments) {
    this.store = store;
    this.dispatch = dispatch;
    this.assignments = assignments;
  }

  public RequestDtos.HistoryView perform(
      UUID id, String subject, String key, Step step, String note) {
    var prepared = store.prepare(id, subject, key, step, note);
    RequestDtos.HistoryView result = prepared.previous();
    if (result == null) {
      var evidence = step == Step.DISPATCH ? dispatch.validate(prepared.input()) : null;
      result = store.commit(prepared.input(), subject, key, step, note, evidence);
    }
    if (step == Step.COMPLETION) assignments.recover(id);
    return result;
  }
}
