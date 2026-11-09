package com.auxiruta.assistance.requests;

import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.auxiruta.assistance.requests.DomainTypes.*;

/** Local transactions only; no remote calls are performed while holding these locks. */
@Service
@Transactional
public class ExecutionStore {
 public enum Step {
  DISPATCH(Status.ASSIGNED,Status.DISPATCHED,"dispatched_at"),
  ARRIVAL(Status.DISPATCHED,Status.ARRIVED,"arrived_at"),
  LOADING(Status.ARRIVED,Status.VEHICLE_LOADED,"loaded_at"),
  TRANSPORT_START(Status.VEHICLE_LOADED,Status.IN_TRANSIT,"transport_started_at"),
  COMPLETION(Status.IN_TRANSIT,Status.COMPLETED,"completed_at");
  final Status previous,next;final String column;
  Step(Status previous,Status next,String column){this.previous=previous;this.next=next;this.column=column;}
  public boolean admits(Status state){return previous==state;}
 }
 public record Input(UUID requestId,long version,UUID assignmentId,UUID truckId,UUID operatorId,String subject,Instant acceptedAt,String rules,BigDecimal originLat,BigDecimal originLon,BigDecimal destinationLat,BigDecimal destinationLon,String mobility) {}
 public record Preparation(Input input,RequestDtos.HistoryView previous) {}
 private final JdbcTemplate jdbc;private final RequestRepository requests;private final HistoryRepository history;private final Clock clock;private final ObjectMapper mapper;
 public ExecutionStore(JdbcTemplate jdbc,RequestRepository requests,HistoryRepository history,Clock clock,ObjectMapper mapper){this.jdbc=jdbc;this.requests=requests;this.history=history;this.clock=clock;this.mapper=mapper;}
 private Instant now(){return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private static void require(boolean value){if(!value)throw new ResponseStatusException(HttpStatus.CONFLICT);}
 static String fingerprint(Step step,String note){return RequestService.digest(step.name()+":"+(note==null?"":note.strip()));}
 private RequestDtos.HistoryView view(RequestStatusHistory h){return new RequestDtos.HistoryView(h.id,h.request.getId(),h.previousStatus,h.newStatus,h.actorType,h.actorSubject,h.reason,h.details,h.operationKey,h.occurredAt);}
 private AssistanceRequest lock(UUID id){UUID vehicle=requests.vehicleId(id).orElseThrow(ExecutionStore::missing);jdbc.queryForObject("select id from vehicles where id=? for update",UUID.class,vehicle);return requests.lockById(id).orElseThrow(ExecutionStore::missing);}
 public Preparation prepare(UUID id,String subject,String key,Step step,String note){
  if(key==null || key.isBlank() || key.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  AssistanceRequest r=lock(id);
  if(jdbc.queryForObject("select count(*) from assignments where request_id=? and operator_subject=?",Long.class,id,subject)==0)throw missing();
  var old=history.findByRequestIdAndOperationKey(id,key);
  if(old.isPresent()){var h=old.get();require(subject.equals(h.actorSubject) && fingerprint(step,note).equals(h.requestFingerprint));return new Preparation(null,view(h));}
  var assignments=jdbc.query("select id,tow_truck_id,operator_id,operator_subject,accepted_at from assignments where request_id=? and status='ACCEPTED'",(rs,n)->new Input(id,r.version,rs.getObject("id",UUID.class),rs.getObject("tow_truck_id",UUID.class),rs.getObject("operator_id",UUID.class),rs.getString("operator_subject"),rs.getTimestamp("accepted_at").toInstant(),r.configurationSnapshot,r.originLatitude,r.originLongitude,r.destinationLatitude,r.destinationLongitude,r.mobilityCondition.name()),id);
  if(assignments.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT);
  var input=assignments.get(0);if(!input.subject().equals(subject))throw missing();require(step.admits(r.status) && r.closedAt==null && !r.resourceReleasePending);
  if(step==Step.DISPATCH)require(dispatchOpen(input.acceptedAt(),now(),dispatchSeconds(input.rules())));
  return new Preparation(input,null);
 }
 static boolean dispatchOpen(Instant acceptedAt,Instant now,int seconds){return !now.isBefore(acceptedAt) && now.isBefore(acceptedAt.plusSeconds(seconds));}
 private int dispatchSeconds(String rules){try{return mapper.readTree(rules).path("execution").path("dispatch-deadline-seconds").asInt();}catch(Exception e){throw new IllegalStateException("Invalid rule snapshot");}}
 public RequestDtos.HistoryView commit(Input expected,String subject,String key,Step step,String note,DispatchValidator.Evidence evidence){
  Preparation preparation=prepare(expected.requestId(),subject,key,step,note);if(preparation.previous()!=null)return preparation.previous();
  Input current=preparation.input();require(current.version()==expected.version() && current.assignmentId().equals(expected.assignmentId()));
  AssistanceRequest r=requests.findById(expected.requestId()).orElseThrow();Instant at=now();
  if(step==Step.DISPATCH)require(evidence!=null && !at.isAfter(evidence.validUntil()) && evidence.qualificationUntil().isAfter(at.plusSeconds(evidence.plannedSeconds())));
  RequestStatusHistory h=new RequestStatusHistory();h.request=r;h.previousStatus=r.status;h.newStatus=step.next;h.actorType="OPERATOR";h.actorSubject=subject;h.reason=step==Step.COMPLETION?note.strip():step.name();h.details=evidence==null?null:evidence.json();h.operationKey=key;h.requestFingerprint=fingerprint(step,note);h.occurredAt=at;
  r.status=step.next;r.updatedAt=at;
  // Column comes exclusively from the enum, never from client input.
  jdbc.update("update assignments set "+step.column+"=? where id=?",Timestamp.from(at),current.assignmentId());
  if(step==Step.COMPLETION){
   r.closedAt=at;r.closureReasonCode="SERVICE_COMPLETED";r.resourceReleasePending=true;
   jdbc.update("update assignments set status='CLOSED',closed_at=?,completion_note=? where id=?",Timestamp.from(at),note.strip(),current.assignmentId());
   jdbc.update("insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at) values (?,?,'RELEASE','PENDING',?,?) on conflict(assignment_id,kind) do nothing",UUID.randomUUID(),current.assignmentId(),Timestamp.from(at),Timestamp.from(at));
  }
  history.saveAndFlush(h);
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){org.slf4j.LoggerFactory.getLogger(ExecutionStore.class).info("Request execution requestId={} from={} to={}",r.id,h.previousStatus,h.newStatus);}});
  return view(h);
 }
}
