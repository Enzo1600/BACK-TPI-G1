package com.auxiruta.assistance.requests;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExecutionServiceTest {
 ExecutionStore store=mock(ExecutionStore.class);DispatchValidator validator=mock(DispatchValidator.class);AssignmentCoordinator assignments=mock(AssignmentCoordinator.class);ExecutionService service=new ExecutionService(store,validator,assignments);
 @Test void replayedCompletionRetriesReleaseWithoutAnotherTransition(){UUID id=UUID.randomUUID();var event=new RequestDtos.HistoryView(UUID.randomUUID(),id,DomainTypes.Status.IN_TRANSIT,DomainTypes.Status.COMPLETED,"OPERATOR","operator","Delivered",null,"key",Instant.EPOCH);when(store.prepare(id,"operator","key",ExecutionStore.Step.COMPLETION,"Delivery confirmed")).thenReturn(new ExecutionStore.Preparation(null,event));assertEquals(event,service.perform(id,"operator","key",ExecutionStore.Step.COMPLETION,"Delivery confirmed"));verify(assignments).recover(id);verifyNoInteractions(validator);verify(store,never()).commit(any(),any(),any(),any(),any(),any());}
 @Test void failedDispatchDoesNotChangeState(){UUID id=UUID.randomUUID();var input=mock(ExecutionStore.Input.class);when(store.prepare(id,"operator","key",ExecutionStore.Step.DISPATCH,null)).thenReturn(new ExecutionStore.Preparation(input,null));when(validator.validate(input)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.perform(id,"operator","key",ExecutionStore.Step.DISPATCH,null));verify(store,never()).commit(any(),any(),any(),any(),any(),any());verifyNoInteractions(assignments);}
}
