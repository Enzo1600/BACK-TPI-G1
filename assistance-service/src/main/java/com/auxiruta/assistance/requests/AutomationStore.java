package com.auxiruta.assistance.requests;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;
/** Durable planning and local decisions. No network calls in these transactions. */
@Service @Transactional
public class AutomationStore {
 public record Work(UUID requestId,UUID assignmentId,boolean evaluate) {}
 private final JdbcTemplate jdbc;private final RequestRepository requests;private final HistoryRepository history;private final EvaluationRepository evaluations;private final ObjectMapper mapper;private final Clock clock;
 public AutomationStore(JdbcTemplate jdbc,RequestRepository requests,HistoryRepository history,EvaluationRepository evaluations,ObjectMapper mapper,Clock clock){this.jdbc=jdbc;this.requests=requests;this.history=history;this.evaluations=evaluations;this.mapper=mapper;this.clock=clock;}
 private Instant now(){return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
 private static Timestamp ts(Instant t){return Timestamp.from(t);}
 private int rule(AssistanceRequest r,String group,String key){try{return mapper.readTree(r.configurationSnapshot).path(group).path(key).asInt();}catch(Exception e){throw new IllegalStateException("Invalid rules");}}
 private AssistanceRequest lock(UUID id){UUID vehicle=requests.vehicleId(id).orElseThrow();jdbc.queryForObject("select id from vehicles where id=? for update",UUID.class,vehicle);return requests.lockById(id).orElseThrow();}
 public void resumeAfterRestart(){jdbc.update("update assistance_requests set evaluation_lease_until='1970-01-01T00:00:00Z',next_evaluation_at=? where closed_at is null and status in ('REQUESTED','PENDING_EVALUATION','AWAITING_ACCEPTANCE','SEARCHING_ALTERNATIVE','ASSIGNED')",ts(now()));}
 public List<UUID> due(){return jdbc.queryForList("select id from assistance_requests where closed_at is null and status in ('REQUESTED','PENDING_EVALUATION','AWAITING_ACCEPTANCE','SEARCHING_ALTERNATIVE','ASSIGNED') and next_evaluation_at<=? order by created_at,id limit 100",UUID.class,ts(now()));}
 private void event(AssistanceRequest r,Status next,String reason){Status old=r.status;r.status=next;r.updatedAt=now();RequestStatusHistory h=new RequestStatusHistory();h.request=r;h.previousStatus=old;h.newStatus=next;h.actorType="SYSTEM";h.reason=reason;h.occurredAt=now();history.save(h);org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){org.slf4j.LoggerFactory.getLogger(AutomationStore.class).info("Automatic transition requestId={} from={} to={} reason={}",r.id,old,next,reason);}});}
 private long pending(UUID id,String kind){return jdbc.queryForObject("select count(*) from pending_actions p join assignments a on a.id=p.assignment_id where a.request_id=? and p.status<>'COMPLETED'"+(kind==null?"":" and p.kind=?"),Long.class,kind==null?new Object[]{id}:new Object[]{id,kind});}
 private void release(UUID assignment){jdbc.update("insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at) values (?,?,'RELEASE','PENDING',?,?) on conflict(assignment_id,kind) do nothing",UUID.randomUUID(),assignment,ts(now()),ts(now()));}
 private void close(AssistanceRequest r,String reason){
  for(var row:jdbc.queryForList("select id,status,expires_at from assignments where request_id=? and status in ('RESERVING','PROPOSED')",r.id)){
   UUID id=(UUID)row.get("id");Timestamp expires=(Timestamp)row.get("expires_at");String state=expires!=null && !expires.toInstant().isAfter(now())?"EXPIRED":"CANCELLED";
   jdbc.update("update assignments set status=?,closed_at=? where id=?",state,ts(now()),id);r.resourceReleasePending=true;
   if(jdbc.queryForObject("select count(*) from pending_actions where assignment_id=? and kind='RESERVE' and status<>'COMPLETED'",Long.class,id)==0)release(id);
  }
  r.replacementPending=false;r.closedAt=now();r.closureReasonCode=reason;event(r,Status.UNFULFILLED,reason);
 }
 private String deadlineReason(AssistanceRequest r){if(pending(r.id,null)>0)return "DEPENDENCY_TIMEOUT";return evaluations.findFirstByRequestIdOrderByEvaluatedAtDescIdDesc(r.id).map(e->"PENDING_DEPENDENCY".equals(e.result)?"DEPENDENCY_TIMEOUT":"NO_AVAILABLE_RESOURCE".equals(e.reasonCode)?"NO_AVAILABLE_RESOURCE":"NO_VIABLE_CANDIDATE").orElse("DEPENDENCY_TIMEOUT");}
 public Work advance(UUID id){
  AssistanceRequest r=lock(id);Instant now=now();if(r.closedAt!=null || !Set.of(Status.REQUESTED,Status.PENDING_EVALUATION,Status.AWAITING_ACCEPTANCE,Status.SEARCHING_ALTERNATIVE,Status.ASSIGNED).contains(r.status) || r.nextEvaluationAt.isAfter(now))return null;
  r.nextEvaluationAt=now.plusSeconds(5);
  // A remote confirmation may already have committed: never infer nonacceptance from elapsed time.
  if(pending(id,"CONFIRM")>0)return null;
  var accepted=jdbc.queryForList("select id,accepted_at from assignments where request_id=? and status='ACCEPTED'",id);
  if(r.status==Status.ASSIGNED){
   if(accepted.isEmpty())return null;var a=accepted.get(0);UUID assignment=(UUID)a.get("id");Instant deadline=((Timestamp)a.get("accepted_at")).toInstant().plusSeconds(rule(r,"execution","dispatch-deadline-seconds"));
   if(!deadline.isAfter(now)){replace(r,assignment,"DISPATCH_TIMEOUT");return null;}
   if(!r.nextReconciliationAt.isAfter(now)){r.nextReconciliationAt=now.plusSeconds(rule(r,"fleet","reconciliation-interval-seconds"));r.nextEvaluationAt=deadline.isBefore(r.nextReconciliationAt)?deadline:r.nextReconciliationAt;return new Work(id,assignment,false);}
   r.nextEvaluationAt=deadline.isBefore(r.nextReconciliationAt)?deadline:r.nextReconciliationAt;return null;
  }
  if(r.status==Status.SEARCHING_ALTERNATIVE && r.replacementPending && !r.resourceReleasePending && pending(id,null)==0){
   if(r.searchAttemptNumber>=1+rule(r,"search","max-reassignments")){close(r,"REASSIGNMENT_LIMIT_REACHED");return null;}
   r.replacementPending=false;r.searchAttemptNumber++;r.searchDeadline=now.plusSeconds(rule(r,"search","attempt-timeout-seconds"));event(r,Status.PENDING_EVALUATION,"REPLACEMENT_SEARCH_STARTED");
  }
  if(r.status==Status.SEARCHING_ALTERNATIVE && r.replacementPending && r.resourceReleasePending)return null;
  if(!r.searchDeadline.isAfter(now)){close(r,deadlineReason(r));return null;}
  if(r.resourceReleasePending || pending(id,null)>0)return null;
  var proposals=jdbc.queryForList("select id,expires_at from assignments where request_id=? and status='PROPOSED'",id);
  if(!proposals.isEmpty()){
   var a=proposals.get(0);Instant expires=((Timestamp)a.get("expires_at")).toInstant();
   if(!expires.isAfter(now)){UUID assignment=(UUID)a.get("id");jdbc.update("update assignments set status='EXPIRED',closed_at=? where id=?",ts(now),assignment);r.resourceReleasePending=true;release(assignment);event(r,Status.SEARCHING_ALTERNATIVE,"PROPOSAL_EXPIRED");}
   else r.nextEvaluationAt=expires.isBefore(r.searchDeadline)?expires:r.searchDeadline;return null;
  }
  if(jdbc.queryForObject("select count(*) from assignments where request_id=? and search_attempt_number=? and expires_at is not null",Long.class,id,r.searchAttemptNumber)>=rule(r,"search","max-offers-per-attempt")){close(r,"OFFER_LIMIT_REACHED");return null;}
  if(r.status==Status.SEARCHING_ALTERNATIVE)event(r,Status.PENDING_EVALUATION,"SEARCH_RESUMED");
  r.nextEvaluationAt=now.plusSeconds(rule(r,"search","evaluation-interval-seconds"));return new Work(id,null,true);
 }
 private void replace(AssistanceRequest r,UUID assignment,String reason){jdbc.update("update assignments set status='CLOSED',closed_at=?,reason=? where id=?",ts(now()),reason,assignment);r.resourceReleasePending=true;r.replacementPending=true;r.nextEvaluationAt=now();release(assignment);event(r,Status.SEARCHING_ALTERNATIVE,reason);}
 public void reconcile(Work work,JsonNode remote){
  AssistanceRequest r=lock(work.requestId());if(r.status!=Status.ASSIGNED || r.closedAt!=null || pending(r.id,"CONFIRM")>0)return;
  if(jdbc.queryForObject("select count(*) from assignments where id=? and request_id=? and status='ACCEPTED'",Long.class,work.assignmentId(),r.id)==0)return;
  if(remote==null || !work.assignmentId().toString().equals(remote.path("assignmentAttemptId").asText()))return;
  String status=remote.path("status").asText();if(status.equals("MISSING"))return; // Infrastructure inconsistency is not authority to free a commitment.
  if((status.equals("CONFIRMED") && remote.path("resource").has("eligible") && !remote.path("resource").path("eligible").asBoolean()) || Set.of("RELEASED","EXPIRED").contains(status))replace(r,work.assignmentId(),"RESOURCE_NOT_ELIGIBLE");
 }
}
