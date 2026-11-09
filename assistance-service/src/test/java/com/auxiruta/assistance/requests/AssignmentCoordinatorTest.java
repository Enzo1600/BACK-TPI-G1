package com.auxiruta.assistance.requests;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import com.auxiruta.assistance.routing.FleetClient;
import com.fasterxml.jackson.databind.ObjectMapper;
class AssignmentCoordinatorTest {
 @org.junit.jupiter.api.BeforeEach void allowClaims(){when(store.claim(any())).thenReturn(true);}
 AssignmentStore store=mock(AssignmentStore.class);FleetClient fleet=mock(FleetClient.class);AssignmentCoordinator coordinator=new AssignmentCoordinator(store,fleet);
 @Test void lostConfirmationResponseRemainsDurableWithoutImmediateRetry(){var action=new AssignmentStore.Action(UUID.randomUUID(),UUID.randomUUID(),"CONFIRM",null);when(fleet.reservation(action.assignmentId(),"CONFIRM",null)).thenThrow(new IllegalStateException("response lost"));coordinator.execute(action);verify(store).failed(action);verify(fleet,times(1)).reservation(action.assignmentId(),"CONFIRM",null);verify(store,never()).completed(any(),any());}
 @Test void cancellationCompensatesTheSameReservationAttempt(){UUID attempt=UUID.randomUUID();var reserve=new AssignmentStore.Action(UUID.randomUUID(),attempt,"RESERVE","{}");var release=new AssignmentStore.Action(UUID.randomUUID(),attempt,"RELEASE",null);var held=new ObjectMapper().createObjectNode().put("status","HELD");var released=new ObjectMapper().createObjectNode().put("status","RELEASED");when(fleet.reservation(attempt,"RESERVE","{}")).thenReturn(held);when(store.completed(reserve,held)).thenReturn(release);when(fleet.reservation(attempt,"RELEASE",null)).thenReturn(released);coordinator.execute(reserve);verify(store).completed(release,released);}
 @Test void recoveryReusesPersistedAttemptAndPayload(){UUID request=UUID.randomUUID();var action=new AssignmentStore.Action(UUID.randomUUID(),UUID.randomUUID(),"RESERVE","original payload");when(store.pendingForRequest(request)).thenReturn(List.of(action));var result=new ObjectMapper().createObjectNode().put("status","EXPIRED");when(fleet.reservation(action.assignmentId(),"RESERVE",action.payload())).thenReturn(result);coordinator.recover(request);verify(fleet).reservation(action.assignmentId(),"RESERVE","original payload");verify(store).completed(action,result);}
 @Test void anotherWorkerCannotExecuteAnAlreadyClaimedAction(){var action=new AssignmentStore.Action(UUID.randomUUID(),UUID.randomUUID(),"CONFIRM",null);when(store.claim(action)).thenReturn(false);coordinator.execute(action);verifyNoInteractions(fleet);}
 @Test void retryAfterHonorsSecondsAndHttpDate(){var now=java.time.Instant.parse("2026-11-08T12:00:00Z");org.junit.jupiter.api.Assertions.assertEquals(120,AssignmentCoordinator.retryAfter("120",now));org.junit.jupiter.api.Assertions.assertEquals(121,AssignmentCoordinator.retryAfter("Sun, 8 Nov 2026 12:02:00 GMT",now));org.junit.jupiter.api.Assertions.assertEquals(60,AssignmentCoordinator.retryAfter("invalid",now));}
}
