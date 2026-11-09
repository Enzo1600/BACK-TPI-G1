package com.auxiruta.assistance.requests;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.assistance.requests.ExecutionStore.Step;
class ExecutionRulesTest {
 @Test void everyActionAcceptsOnlyItsPredecessor(){for(Step step:Step.values())for(DomainTypes.Status status:DomainTypes.Status.values())assertEquals(status==step.previous,step.admits(status));}
 @Test void dispatchDeadlineIsExclusive(){Instant accepted=Instant.parse("2026-11-08T12:00:00Z");assertTrue(ExecutionStore.dispatchOpen(accepted,accepted.plusSeconds(299),300));assertFalse(ExecutionStore.dispatchOpen(accepted,accepted.plusSeconds(300),300));assertFalse(ExecutionStore.dispatchOpen(accepted,accepted.minusSeconds(1),300));}
 @Test void sameKeyCannotRepresentAnotherStepOrNote(){assertNotEquals(ExecutionStore.fingerprint(Step.ARRIVAL,null),ExecutionStore.fingerprint(Step.LOADING,null));assertNotEquals(ExecutionStore.fingerprint(Step.COMPLETION,"First delivery note"),ExecutionStore.fingerprint(Step.COMPLETION,"Other delivery note"));assertEquals(ExecutionStore.fingerprint(Step.COMPLETION,"Delivery confirmed"),ExecutionStore.fingerprint(Step.COMPLETION," Delivery confirmed "));}
}
