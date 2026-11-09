package com.auxiruta.assistance.requests;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.auxiruta.assistance.routing.FleetClient;
@Component @EnableScheduling @ConditionalOnProperty(name="auxiruta.automation.enabled",havingValue="true",matchIfMissing=true)
public class AutomationJobs {
 private final AutomationStore store;private final EvaluationService evaluations;private final AssignmentCoordinator assignments;private final FleetClient fleet;
 public AutomationJobs(AutomationStore store,EvaluationService evaluations,AssignmentCoordinator assignments,FleetClient fleet){this.store=store;this.evaluations=evaluations;this.assignments=assignments;this.fleet=fleet;}
 @org.springframework.context.annotation.Bean
 public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler(){var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("automation-");return scheduler;}
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 public void resume(){store.resumeAfterRestart();}
 @Scheduled(fixedDelay=1000,initialDelay=1000)
 public void recover(){try{assignments.recoverDue();}catch(Exception e){log("recovery",e);}}
 @Scheduled(fixedDelay=1000,initialDelay=2000)
 public void process(){
  List<AutomationStore.Work> checks=new ArrayList<>();
  for(UUID id:store.due())try{var work=store.advance(id);if(work==null)continue;if(work.evaluate()){evaluations.evaluate(id);assignments.propose(id);}else checks.add(work);}catch(Exception e){log("request="+id,e);}
  if(!checks.isEmpty())try{var states=fleet.statuses(checks.stream().map(AutomationStore.Work::assignmentId).toList());if(states!=null)for(var work:checks)states.stream().filter(s->work.assignmentId().toString().equals(s.path("assignmentAttemptId").asText())).findFirst().ifPresent(s->store.reconcile(work,s));}catch(Exception e){log("fleet-reconciliation",e);}
 }
 private void log(String task,Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Automatic task deferred task={} type={}",task,e.getClass().getSimpleName());}
}
