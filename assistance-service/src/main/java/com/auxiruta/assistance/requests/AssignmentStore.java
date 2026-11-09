package com.auxiruta.assistance.requests;
import java.util.*;
import java.time.*;
import java.sql.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;
import com.auxiruta.commons.security.TokenSupport;
import com.fasterxml.jackson.databind.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;
@Service @Transactional
public class AssignmentStore {
 public record View(UUID id,UUID requestId,UUID towTruckId,UUID operatorId,String operatorSubject,String status,Instant expiresAt,Instant acceptedAt,String reason) {}
 public record Action(UUID id,UUID assignmentId,String kind,String payload) {}
 private final JdbcTemplate jdbc;private final RequestRepository requests;private final CandidateEvaluationRepository candidates;private final EvaluationRepository evaluations;private final HistoryRepository history;private final Clock clock;private final ObjectMapper mapper;
 public AssignmentStore(JdbcTemplate jdbc,RequestRepository requests,CandidateEvaluationRepository candidates,EvaluationRepository evaluations,HistoryRepository history,Clock clock,ObjectMapper mapper){this.jdbc=jdbc;this.requests=requests;this.candidates=candidates;this.evaluations=evaluations;this.history=history;this.clock=clock;this.mapper=mapper;}
 private int rule(AssistanceRequest r,String group,String key){try{return mapper.readTree(r.configurationSnapshot).path(group).path(key).asInt();}catch(Exception e){throw new IllegalStateException("Invalid rules");}}
 private Instant now(){return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
 private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}
 private static Instant instant(ResultSet r,String name)throws SQLException {Timestamp t=r.getTimestamp(name);return t==null?null:t.toInstant();}
 private static void require(boolean ok){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private View map(ResultSet r,int row)throws SQLException{return new View(r.getObject("id",UUID.class),r.getObject("request_id",UUID.class),r.getObject("tow_truck_id",UUID.class),r.getObject("operator_id",UUID.class),r.getString("operator_subject"),r.getString("status"),instant(r,"expires_at"),instant(r,"accepted_at"),r.getString("reason"));}
 private View get(UUID id){return jdbc.query("select * from assignments where id=?",this::map,id).stream().findFirst().orElseThrow(AssignmentStore::missing);}
 private AssistanceRequest lock(UUID requestId){UUID vehicle=requests.vehicleId(requestId).orElseThrow(AssignmentStore::missing);jdbc.queryForObject("select id from vehicles where id=? for update",UUID.class,vehicle);return requests.lockById(requestId).orElseThrow(AssignmentStore::missing);}
 public View visible(UUID id,Jwt jwt){View a=get(id);if(!TokenSupport.permits(jwt,false,"ADMIN") && !a.operatorSubject().equals(jwt.getSubject()))throw missing();return a;}
 public RequestDtos.PageView<View> own(String subject,int page,int size){if(page<0 || size<1 || size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);long count=jdbc.queryForObject("select count(*) from assignments where operator_subject=?",Long.class,subject);return new RequestDtos.PageView<>(jdbc.query("select * from assignments where operator_subject=? order by created_at,id limit ? offset ?",this::map,subject,size,(long)page*size),page,size,count,(int)((count+size-1)/size));}
 private void event(AssistanceRequest r,Status next,String reason,String actor){Status previous=r.status;r.status=next;r.updatedAt=now();r.nextEvaluationAt=now();if(next==Status.ASSIGNED)r.nextReconciliationAt=now();RequestStatusHistory h=new RequestStatusHistory();h.request=r;h.previousStatus=previous;h.newStatus=next;h.actorType=actor==null?"SYSTEM":"OPERATOR";h.actorSubject=actor;h.reason=reason;h.occurredAt=now();history.save(h);
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){org.slf4j.LoggerFactory.getLogger(AssignmentStore.class).info("Request state changed requestId={} from={} to={} reason={}",r.id,previous,next,reason);}});
 }
 private Action action(UUID assignment,String kind,String payload){UUID id=UUID.randomUUID();jdbc.update("insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at) values (?,?,?,'PENDING',?,?) on conflict(assignment_id,kind) do nothing",id,assignment,kind,ts(now()),ts(now()));return jdbc.query("select id from pending_actions where assignment_id=? and kind=? and status<>'COMPLETED'",(rs,n)->new Action(rs.getObject(1,UUID.class),assignment,kind,payload),assignment,kind).stream().findFirst().orElse(null);}
 public Action propose(UUID requestId){
  AssistanceRequest r=lock(requestId);if(r.closedAt!=null || r.resourceReleasePending || r.evaluationLeaseUntil.isAfter(now()) || r.status!=Status.PENDING_EVALUATION || !r.searchDeadline.isAfter(now()))return null;
  if(jdbc.queryForObject("select count(*) from assignments where request_id=? and (status in ('RESERVING','PROPOSED','ACCEPTED'))",Long.class,requestId)>0)return null;
  if(jdbc.queryForObject("select count(*) from assignments where request_id=? and search_attempt_number=? and expires_at is not null",Long.class,requestId,r.searchAttemptNumber)>=rule(r,"search","max-offers-per-attempt"))return null;
  var maybe=evaluations.findFirstByRequestIdOrderByEvaluatedAtDescIdDesc(requestId);if(maybe.isEmpty() || !"VIABLE".equals(maybe.get().result))return null;
  var e=maybe.get();if(e.evaluatedAt.isBefore(now().minusSeconds(300)))return null;
  Set<UUID> tried=new HashSet<>(jdbc.queryForList("select tow_truck_id from assignments where request_id=? and search_attempt_number=? and (expires_at is not null or created_at>?)",UUID.class,requestId,r.searchAttemptNumber,ts(now().minusSeconds(rule(r,"search","evaluation-interval-seconds")))));
  Set<UUID> rejectedOperators=new HashSet<>(jdbc.queryForList("select distinct operator_id from assignments where request_id=? and response_kind='REJECT'",UUID.class,requestId));
  var choice=candidates.findByEvaluationId(e.id).stream().filter(c->c.eligible && c.rankingPosition!=null && !tried.contains(c.towTruckId) && !rejectedOperators.contains(c.operatorId)).sorted(Comparator.comparingInt(c->c.rankingPosition)).findFirst();if(choice.isEmpty())return null;
  var c=choice.get();if(c.truckLocationUpdatedAt.isBefore(now().minusSeconds(300)) || c.approachRouteObtainedAt.isBefore(now().minusSeconds(300)) || c.transportRouteObtainedAt.isBefore(now().minusSeconds(300)) || Duration.between(now(),r.searchDeadline).getSeconds()<rule(r,"search","minimum-offer-window-seconds"))return null;
  // Preserve the planned duration while moving its reference instant to reservation time.
  Instant end=now().plusSeconds(Math.min(rule(r,"fleet","hold-seconds"),Duration.between(now(),r.searchDeadline).getSeconds())+rule(r,"execution","dispatch-deadline-seconds")+c.adjustedApproachSeconds+e.loadingSeconds+e.adjustedTransportSeconds+e.unloadingSeconds+e.operationalMarginSeconds);
  var body=mapper.createObjectNode();body.put("holdSeconds",rule(r,"fleet","hold-seconds"));body.put("minimumOfferSeconds",rule(r,"search","minimum-offer-window-seconds"));body.put("requestId",requestId.toString());body.put("towTruckId",c.towTruckId.toString());body.put("operatorId",c.operatorId.toString());body.put("vehicleType",r.declaredVehicleType.name());body.put("weightKg",r.declaredWeightKg);body.put("nonRollable",r.mobilityCondition==Mobility.NON_ROLLABLE);body.put("searchDeadline",r.searchDeadline.toString());body.put("estimatedServiceEndAt",end.toString());
  UUID id=UUID.randomUUID();jdbc.update("insert into assignments(id,request_id,candidate_evaluation_id,tow_truck_id,operator_id,operator_subject,status,created_at,search_attempt_number,reserve_payload) values (?,?,?,?,?,?,'RESERVING',?,?,?)",id,requestId,c.id,c.towTruckId,c.operatorId,c.operatorSubject,ts(now()),r.searchAttemptNumber,body.toString());
  return action(id,"RESERVE",body.toString());
 }
 public Action respond(UUID id,String subject,String key,boolean accept,String reason){
  if(key==null || key.isBlank() || key.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  View initial=get(id);if(!initial.operatorSubject().equals(subject))throw missing();AssistanceRequest r=lock(initial.requestId());View a=get(id);
  String kind=accept?"ACCEPT":"REJECT",fingerprint=RequestService.digest(kind+":"+(reason==null?"":reason.strip()));
  var previous=jdbc.queryForMap("select response_key,response_fingerprint,response_kind from assignments where id=?",id);
  if(previous.get("response_key")!=null){require(key.equals(previous.get("response_key")) && fingerprint.equals(previous.get("response_fingerprint")));return pending(id);}
  require(r.closedAt==null && a.status().equals("PROPOSED") && a.expiresAt().isAfter(now()) && r.searchDeadline.isAfter(now()));
  jdbc.update("update assignments set response_key=?,response_fingerprint=?,response_kind=?,reason=? where id=?",key,fingerprint,kind,reason,id);
  // An active proposal retains its admitted route evidence until expiresAt; Fleet locks location while HELD.
  // Dispatch independently revalidates routes. Acceptance still requires Fleet confirmation before its deadline.
  if(accept)return action(id,"CONFIRM",null);
  jdbc.update("update assignments set status='REJECTED',closed_at=? where id=?",ts(now()),id);r.resourceReleasePending=true;event(r,Status.PENDING_EVALUATION,"OPERATOR_REJECTED",subject);return action(id,"RELEASE",null);
 }
 public Action pending(UUID id){return jdbc.query("select p.id,p.kind,a.reserve_payload from pending_actions p join assignments a on a.id=p.assignment_id where a.id=? and p.status<>'COMPLETED' order by p.created_at limit 1",(rs,n)->new Action(rs.getObject(1,UUID.class),id,rs.getString(2),rs.getString(3)),id).stream().findFirst().orElse(null);}
 public List<Action> pendingForRequest(UUID requestId){return jdbc.query("select p.id,p.assignment_id,p.kind,a.reserve_payload from pending_actions p join assignments a on a.id=p.assignment_id where a.request_id=? and p.status<>'COMPLETED' order by p.created_at",(rs,n)->new Action(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4)),requestId);}
 public boolean claim(Action action){return jdbc.update("update pending_actions set next_attempt_at=? where id=? and status<>'COMPLETED' and next_attempt_at<=?",ts(now().plusSeconds(30)),action.id(),ts(now()))==1;}
 public static int retryDelay(int failures){return failures<=0?5:failures==1?15:failures==2?30:60;}
 public void failed(Action action){defer(action,0);}
 public void defer(Action action,int minimum){Integer failures=jdbc.queryForObject("select failure_count from pending_actions where id=?",Integer.class,action.id());int delay=Math.max(minimum,retryDelay(failures));jdbc.update("update pending_actions set status='RETRYING',failure_count=failure_count+1,updated_at=?,next_attempt_at=? where id=? and status<>'COMPLETED'",ts(now()),ts(now().plusSeconds(delay)),action.id());}
 public List<Action> dueActions(){return jdbc.query("select p.id,p.assignment_id,p.kind,a.reserve_payload from pending_actions p join assignments a on a.id=p.assignment_id where p.status<>'COMPLETED' and p.next_attempt_at<=? order by p.next_attempt_at,p.id limit 100",(rs,n)->new Action(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4)),ts(now()));}
 public Action completed(Action action,JsonNode remote){
  View initial=get(action.assignmentId());AssistanceRequest r=lock(initial.requestId());View a=get(initial.id());
  String actionState=jdbc.queryForObject("select status from pending_actions where id=?",String.class,action.id());if("COMPLETED".equals(actionState))return pending(a.id());
  String status=remote.path("status").asText();require(Set.of("HELD","CONFIRMED","RELEASED","EXPIRED").contains(status));
  jdbc.update("update pending_actions set status='COMPLETED',updated_at=? where id=?",ts(now()),action.id());
  if(action.kind().equals("RESERVE")){
   if(!remote.path("expiresAt").isMissingNode() && !remote.path("expiresAt").isNull() && (status.equals("HELD") || remote.path("createdHold").asBoolean()))jdbc.update("update assignments set expires_at=? where id=?",ts(Instant.parse(remote.path("expiresAt").asText())),a.id());
   if(r.closedAt!=null){r.resourceReleasePending=true;return action(a.id(),"RELEASE",null);}
   if(status.equals("HELD")){Instant expiry=Instant.parse(remote.path("expiresAt").asText());
    if(Duration.between(now(),expiry).getSeconds()<rule(r,"search","minimum-offer-window-seconds")){jdbc.update("update assignments set status='EXPIRED',closed_at=? where id=?",ts(now()),a.id());r.resourceReleasePending=true;event(r,Status.PENDING_EVALUATION,"PROPOSAL_WINDOW_TOO_SHORT",null);return action(a.id(),"RELEASE",null);}
    jdbc.update("update assignments set status='PROPOSED',expires_at=? where id=?",ts(expiry),a.id());event(r,Status.AWAITING_ACCEPTANCE,"PROPOSAL_CREATED",null);}
   else{jdbc.update("update assignments set status='EXPIRED',closed_at=? where id=?",ts(now()),a.id());event(r,Status.PENDING_EVALUATION,"RESERVATION_EXPIRED",null);}
  }else if(action.kind().equals("CONFIRM")){
   if(status.equals("CONFIRMED")){require(r.closedAt==null);Instant confirmed=Instant.parse(remote.path("confirmedAt").asText());jdbc.update("update assignments set status='ACCEPTED',accepted_at=? where id=?",ts(confirmed),a.id());if(r.firstAssignedAt==null)r.firstAssignedAt=confirmed;event(r,Status.ASSIGNED,"OPERATOR_ACCEPTED",a.operatorSubject());}
   else{jdbc.update("update assignments set status='EXPIRED',closed_at=? where id=?",ts(now()),a.id());r.resourceReleasePending=true;event(r,Status.PENDING_EVALUATION,"CONFIRMATION_NOT_VALID",null);return action(a.id(),"RELEASE",null);}
  }else{require(status.equals("RELEASED") || status.equals("EXPIRED"));r.resourceReleasePending=false;}
  return null;
 }
 public void reserveRejected(Action action){View a=get(action.assignmentId());AssistanceRequest r=lock(a.requestId());jdbc.update("update assignments set status='EXPIRED',closed_at=?,reason='RESOURCE_UNAVAILABLE' where id=?",ts(now()),a.id());jdbc.update("update pending_actions set status='COMPLETED',updated_at=? where id=?",ts(now()),action.id());if(r.closedAt==null)event(r,Status.PENDING_EVALUATION,"RESOURCE_UNAVAILABLE",null);else r.resourceReleasePending=false;}
}
