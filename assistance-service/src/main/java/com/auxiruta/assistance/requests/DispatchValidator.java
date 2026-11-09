package com.auxiruta.assistance.requests;

import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.*;
import com.auxiruta.assistance.routing.*;
import static com.auxiruta.assistance.routing.RouteProvider.*;

/** Rechecks the currently committed resource, not the list of free candidates. */
@Service
public class DispatchValidator {
 public record Evidence(String json,Instant validUntil,Instant qualificationUntil,long plannedSeconds) {}
 private final FleetClient fleet;private final RouteProvider routes;private final ObjectMapper mapper;private final Clock clock;
 public DispatchValidator(FleetClient fleet,RouteProvider routes,ObjectMapper mapper,Clock clock){this.fleet=fleet;this.routes=routes;this.mapper=mapper;this.clock=clock;}
 private static void require(boolean value){if(!value)throw new ResponseStatusException(HttpStatus.CONFLICT,"DISPATCH_NOT_ELIGIBLE");}
 private static int value(JsonNode rules,String group,String name){return rules.path(group).path(name).asInt();}
 public Evidence validate(ExecutionStore.Input input){
  try{
   JsonNode rules=mapper.readTree(input.rules());var resource=fleet.eligibility(input.assignmentId());
   if(resource==null || !input.assignmentId().equals(resource.assignmentAttemptId()) || !input.requestId().equals(resource.requestId()) || !input.truckId().equals(resource.towTruckId()) || !input.operatorId().equals(resource.operatorId()) || !input.subject().equals(resource.operatorSubject()))throw new IllegalStateException("FLEET_INVALID_RESPONSE");
   require(resource.eligible());
   Instant now=clock.instant();int routeAge=value(rules,"routing","max-route-age-seconds"),snap=value(rules,"routing","max-snap-distance-meters");
   if(resource.latitude()==null || resource.longitude()==null || resource.locationUpdatedAt()==null || resource.qualificationValidUntil()==null || resource.checkedAt()==null)throw new IllegalStateException("FLEET_INVALID_RESPONSE");
   require(!resource.locationUpdatedAt().isAfter(now) && !resource.checkedAt().isAfter(now));
   require(EvaluationService.inCoverage(resource.latitude(),resource.longitude(),rules) && EvaluationService.inCoverage(input.originLat(),input.originLon(),rules) && EvaluationService.inCoverage(input.destinationLat(),input.destinationLon(),rules));
   Point origin=new Point(input.originLat(),input.originLon()),destination=new Point(input.destinationLat(),input.destinationLon()),truck=new Point(resource.latitude(),resource.longitude());String revision=rules.path("rules-version").asText();
   Route transport=routes.route(origin,destination,revision,routeAge,snap);
   Route approach=truck.latitude().compareTo(origin.latitude())==0 && truck.longitude().compareTo(origin.longitude())==0?new Route(BigDecimal.ZERO,BigDecimal.ZERO,transport.obtainedAt()):routes.route(truck,origin,revision,routeAge,snap);
   BigDecimal factor=rules.path("routing").path("duration-factor").decimalValue();int adjustedTransport=EvaluationService.adjusted(transport.durationSeconds(),factor),adjustedApproach=EvaluationService.adjusted(approach.durationSeconds(),factor),dispatch=value(rules,"execution","dispatch-deadline-seconds");
   require(transport.distanceM().compareTo(BigDecimal.valueOf(value(rules,"routing","min-transport-meters")))>=0 && transport.distanceM().compareTo(BigDecimal.valueOf(value(rules,"routing","max-transport-meters")))<=0 && adjustedTransport<=value(rules,"routing","max-adjusted-transport-seconds"));
   require(approach.distanceM().compareTo(BigDecimal.valueOf(value(rules,"routing","max-approach-meters")))<=0 && (long)dispatch+adjustedApproach+value(rules,"routing","arrival-margin-seconds")<=value(rules,"routing","max-arrival-budget-seconds"));
   long planned=(long)dispatch+adjustedApproach+adjustedTransport+value(rules,"execution",input.mobility().equals("ROLLABLE")?"rollable-loading-seconds":"non-rollable-loading-seconds")+value(rules,"execution","unloading-seconds")+value(rules,"routing","planning-contingency-seconds");
   Instant validUntil=transport.obtainedAt().plusSeconds(routeAge);for(Instant expiry:java.util.List.of(approach.obtainedAt().plusSeconds(routeAge),transport.obtainedAt().plusSeconds(routeAge)))if(expiry.isBefore(validUntil))validUntil=expiry;
   now=clock.instant();require(!now.isAfter(validUntil) && resource.qualificationValidUntil().isAfter(now.plusSeconds(planned)));
   var evidence=mapper.createObjectNode();evidence.put("assignmentId",input.assignmentId().toString());evidence.put("fleetCheckedAt",resource.checkedAt().toString());evidence.put("locationUpdatedAt",resource.locationUpdatedAt().toString());evidence.put("latitude",resource.latitude());evidence.put("longitude",resource.longitude());evidence.put("approachDistanceM",approach.distanceM());evidence.put("transportDistanceM",transport.distanceM());evidence.put("adjustedApproachSeconds",adjustedApproach);evidence.put("adjustedTransportSeconds",adjustedTransport);evidence.put("approachRouteObtainedAt",approach.obtainedAt().toString());evidence.put("transportRouteObtainedAt",transport.obtainedAt().toString());evidence.put("qualificationValidUntil",resource.qualificationValidUntil().toString());evidence.put("estimatedServiceEndAt",now.plusSeconds(planned).toString());
   return new Evidence(evidence.toString(),validUntil,resource.qualificationValidUntil(),planned);
  }catch(ResponseStatusException e){throw e;}catch(RoutingFailure e){throw new ResponseStatusException(e.domainRejection()?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE);}catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);}
 }
}
