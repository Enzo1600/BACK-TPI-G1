package com.auxiruta.assistance.requests;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.auxiruta.assistance.routing.FleetClient;
import org.springframework.web.client.RestClientResponseException;
@Service
public class AssignmentCoordinator {
 private final AssignmentStore store;private final FleetClient fleet;private final java.time.Clock clock;
 public AssignmentCoordinator(AssignmentStore store,FleetClient fleet){this(store,fleet,java.time.Clock.systemUTC());}
 @org.springframework.beans.factory.annotation.Autowired
 public AssignmentCoordinator(AssignmentStore store,FleetClient fleet,java.time.Clock clock){this.store=store;this.fleet=fleet;this.clock=clock;}
 private int retryAfter(RestClientResponseException e){return retryAfter(e.getResponseHeaders()==null?null:e.getResponseHeaders().getFirst("Retry-After"),clock.instant());}
 static int retryAfter(String header,java.time.Instant now){
  if(header==null)return 60;
  try{return (int)Math.min(Integer.MAX_VALUE,Math.max(1,Long.parseLong(header.strip())));}catch(Exception ignored){}
  try{var until=java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();return (int)Math.min(Integer.MAX_VALUE,Math.max(1,java.time.Duration.between(now,until).getSeconds()+1));}catch(Exception ignored){return 60;}
 }
 public void recoverDue(){for(var action:store.dueActions())execute(action);}
 public void propose(UUID request){for(int i=0;i<5;i++){var action=store.propose(request);if(action==null)return;execute(action);}}
 public void recover(UUID request){for(var action:store.pendingForRequest(request))execute(action);}
 public void execute(AssignmentStore.Action action){
  // Each network call is outside database transactions. Continuations are compensation, not retries.
  for(int step=0;action!=null && step<3;step++){
   if(!store.claim(action))return;
   try{var response=fleet.reservation(action.assignmentId(),action.kind(),action.payload());action=store.completed(action,response);}
   catch(RestClientResponseException e){if(action.kind().equals("RESERVE") && (e.getStatusCode().value()==409 || e.getStatusCode().value()==404)){store.reserveRejected(action);return;}store.defer(action,e.getStatusCode().value()==401 || e.getStatusCode().value()==403?300:e.getStatusCode().value()==429?retryAfter(e):0);return;}
   catch(Exception e){store.failed(action);return;}
  }
 }
}
