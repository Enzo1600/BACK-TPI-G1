package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

/** Opt-in: requires the local Docker stack. Credentials/tokens never enter test output. */
@EnabledIfSystemProperty(named="auxiruta.integration",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendIntegrationTest {
 @BeforeAll static void setup() throws Exception { initialize(); }
 @AfterAll static void finish() throws Exception { cleanup();System.out.println("Java integration HTTP checks: "+checks.get()); }
 static String persistedRequestId;
 static JsonNode persistedRequest;
 private static String api(String path) { return "/api/v1"+path; }
 private static JsonNode a(String method,String path,JsonNode body,int expected) throws Exception {return call("ADMIN",method,api(path),body,expected);}
 private static JsonNode o(String method,String path,JsonNode body,int expected) throws Exception {return call("OPERATOR",method,api(path),body,expected);}
 private static JsonNode d(String method,String path,JsonNode body,int expected) throws Exception {return call("DRIVER",method,api(path),body,expected);}
 private static JsonNode dk(String method,String path,JsonNode body,String key,int expected) throws Exception {return call("DRIVER",method,api(path),body,key,expected);}
 @Test @Order(1) void identityAndSecurityRegression() throws Exception {
  for(String role:List.of("DRIVER","OPERATOR","ADMIN","TECH")) {
   JsonNode c=claims(TOKENS.get(role));assertEquals(ISSUER,c.path("iss").asText());assertFalse(c.path("sub").asText().isBlank());
   String audience=role.equals("TECH")?"auxiruta-fleet":"auxiruta-api";
   assertTrue(c.path("aud").isArray()?c.path("aud").toString().contains(audience):c.path("aud").asText().equals(audience));
   assertEquals(role.equals("TECH")?"assistance-service":"auxiruta-api",c.path("azp").asText());
   String roles=c.path("realm_access").path("roles").toString();assertTrue(roles.contains(role.equals("TECH")?"FLEET_SERVICE":role));
   if(role.equals("TECH")){for(String human:List.of("DRIVER","OPERATOR","ADMIN"))assertFalse(roles.contains(human));}else assertFalse(roles.contains("FLEET_SERVICE"));
  }
  assertEquals(401,formPost(OIDC+"/token",Map.of("grant_type","client_credentials","client_id","assistance-service","client_secret","incorrect"),null).statusCode());
  for(String service:List.of("fleet","assistance")) {
   String path="/docs/"+service+"/v3/api-docs";call(null,"GET",path,null,401);
   for(String role:List.of("DRIVER","OPERATOR","ADMIN"))assertTrue(call(role,"GET",path,null,200).has("openapi"));
  }
  call("DRIVER","GET",api("/operators"),null,403);a("POST","/vehicles",object(),403);a("PATCH","/operators/me",object(),403);
  call("ADMIN","GET","/internal/fleet/candidates",null,403);d("GET","/vehicles",null,200);a("GET","/operators",null,200);
  call("TECH","GET","/docs/fleet/v3/api-docs",null,401);
  String[] token=TOKENS.get("DRIVER").split("[.]");token[2]=(token[2].startsWith("A")?"B":"A")+token[2].substring(1);TOKENS.put("TAMPERED",String.join(".",token));call("TAMPERED","GET","/docs/fleet/v3/api-docs",null,401);
  direct("fleet-service",8081,"/internal/fleet/candidates",null,401);direct("fleet-service",8081,"/internal/fleet/candidates","ADMIN",403);direct("fleet-service",8081,"/internal/fleet/candidates","TECH",400);direct("fleet-service",8081,"/internal/fleet/candidates?latitude=-31.4167&longitude=-64.1833&vehicleType=CAR&weightKg=1000&nonRollable=false","TECH",200);
  direct("fleet-service",8081,"/api/v1/operators","DRIVER",403);direct("assistance-service",8082,"/api/v1/vehicles","ADMIN",403);direct("assistance-service",8082,"/api/v1/vehicles","TECH",401);
 }
 @Test @Order(2) void fleetAdministrationRegression() throws Exception {
  String subject=claims(TOKENS.get("OPERATOR")).path("sub").asText();var own=raw("OPERATOR","GET",api("/operators/me"),null,null);
  assertTrue(own.status()==200 || own.status()==404);
  JsonNode operator=own.status()==200?own.body():a("POST","/operators",object("keycloakSubject",subject,"fullName","Java Fleet integration","contactPhone",""),201);
  String op=id(operator);var current=raw("OPERATOR","GET",api("/tow-trucks/assigned-to-me"),null,null);
  assertTrue(current.status()==200 || current.status()==404);
  if(current.status()==200){assertTrue(current.body().path("licensePlate").asText().startsWith("TEST"),"Demo operator linked to a non-test truck");a("DELETE","/tow-trucks/"+id(current.body())+"/operator",null,204);}
  a("PATCH","/operators/"+op+"/registration-status",object("status","ACTIVE"),200);
  a("PATCH","/operators/"+op,object("fullName","Java Fleet integration"),200);
  a("POST","/operators",object("keycloakSubject",subject,"fullName","Duplicate"),409);a("GET","/operators/"+op,null,200);a("GET","/operators?registrationStatus=ACTIVE&size=1",null,200);
  for(String query:List.of("size=101","size=0","page=-1"))a("GET","/operators?"+query,null,400);
  a("GET","/operators/"+key(),null,404);o("GET","/operators",null,403);a("GET","/operators/me",null,403);
  a("PATCH","/operators/"+op,object("fullName","  "),400);a("PATCH","/operators/"+op,object("keycloakSubject","another"),400);
  ObjectNode qualification=object("category","LIGHT_TOWING","validFrom",Instant.now().minusSeconds(86400).toString(),"validUntil",Instant.now().plusSeconds(31536000).toString());
  a("PUT","/operators/"+op+"/qualification",qualification,200);
  a("PUT","/operators/"+op+"/qualification",qualification.deepCopy().put("validUntil",qualification.path("validFrom").asText()),400);
  a("PUT","/operators/"+op+"/qualification",qualification.deepCopy().put("category","OTHER"),400);
  JsonNode available=o("PATCH","/operators/me/availability",object("status","AVAILABLE"),200);
  assertEquals(available.path("availableSince"),o("PATCH","/operators/me/availability",object("status","AVAILABLE"),200).path("availableSince"));
  a("PATCH","/operators/"+op+"/registration-status",object("status","INACTIVE"),200);o("PATCH","/operators/me/availability",object("status","AVAILABLE"),409);
  a("PATCH","/operators/"+op+"/registration-status",object("status","ACTIVE"),200);o("PATCH","/operators/me/availability",object("status","AVAILABLE"),200);
  String plate="TEST"+key().replace("-","").substring(0,6).toUpperCase();
  ObjectNode body=object("licensePlate",plate.toLowerCase(),"maximumLoadKg",3000,"supportsNonRollable",true,"supportedTypes",List.of("CAR","SUV"));
  JsonNode truck=a("POST","/tow-trucks",body,201);String tid=id(truck);
  assertEquals(plate,truck.path("licensePlate").asText());assertEquals("INACTIVE",truck.path("operationalStatus").asText());assertTrue(truck.path("operatorId").isNull());
  a("POST","/tow-trucks",body.deepCopy().put("licensePlate",plate),409);
  for(int weight:List.of(499,5001))a("POST","/tow-trucks",body.deepCopy().put("maximumLoadKg",weight),400);
  a("POST","/tow-trucks",body.deepCopy().set("supportedTypes",JSON.createArrayNode()),400);a("POST","/tow-trucks",body.deepCopy().put("operationalStatus","ACTIVE"),400);o("POST","/tow-trucks",body,403);
  a("GET","/tow-trucks?operationalStatus=INACTIVE&size=2",null,200);a("GET","/tow-trucks/"+tid,null,200);
  a("PATCH","/tow-trucks/"+tid,object("maximumLoadKg",5000,"supportedTypes",List.of("CAR","SUV","PICKUP")),200);a("PATCH","/tow-trucks/"+tid,object("maximumLoadKg",5001),400);
  String other=id(a("POST","/tow-trucks",body.deepCopy().put("licensePlate","TEST"+key().replace("-","").substring(0,6)),201));
  try(var pool=Executors.newFixedThreadPool(2)) {
   var first=pool.submit(()->raw("ADMIN","PUT",api("/tow-trucks/"+tid+"/operator"),object("operatorId",op),null));
   var second=pool.submit(()->raw("ADMIN","PUT",api("/tow-trucks/"+other+"/operator"),object("operatorId",op),null));
   var x=first.get();var y=second.get();assertEquals(List.of(200,409),java.util.stream.Stream.of(x.status(),y.status()).sorted().toList());
   String winner=id(x.status()==200?x.body():y.body());
   assertEquals(winner,id(o("GET","/tow-trucks/assigned-to-me",null,200)));a("PUT","/tow-trucks/"+winner+"/operator",object("operatorId",op),200);
   assertFalse(o("PUT","/tow-trucks/assigned-to-me/location",object("latitude",-31.4167,"longitude",-64.1833),200).path("locationUpdatedAt").isNull());
   o("PUT","/tow-trucks/assigned-to-me/location",object("latitude",91,"longitude",0),400);
   o("PUT","/tow-trucks/assigned-to-me/location",object("latitude",-31.1234567,"longitude",0),400);
   o("PUT","/tow-trucks/assigned-to-me/location",object("latitude",0,"longitude",0,"locationUpdatedAt","2030-01-01T00:00:00Z"),400);
   a("PUT","/tow-trucks/assigned-to-me/location",object("latitude",0,"longitude",0),403);
   String statePath=api("/tow-trucks/"+winner+"/operational-status"),operation=key();ObjectNode change=object("status","ACTIVE","reason","Java integration");
   JsonNode event=call("ADMIN","PATCH",statePath,change,operation,200);assertEquals(event,call("ADMIN","PATCH",statePath,change,operation,200));
   call("ADMIN","PATCH",statePath,change.deepCopy().put("reason","Different"),operation,409);call("ADMIN","PATCH",statePath,change,400);
   call("ADMIN","PATCH",statePath,object("status","MAINTENANCE","reason",""),key(),400);call("ADMIN","PATCH",statePath,object("status","MAINTENANCE","reason","Test"),key(),200);
   assertEquals(event,call("ADMIN","PATCH",statePath,change,operation,200));assertEquals("MAINTENANCE",a("GET","/tow-trucks/"+winner,null,200).path("operationalStatus").asText());
   call("ADMIN","PATCH",statePath,object("status","INACTIVE","reason","Test finished"),key(),200);
   a("DELETE","/tow-trucks/"+winner+"/operator",null,204);a("DELETE","/tow-trucks/"+winner+"/operator",null,204);
  }
  o("GET","/tow-trucks/assigned-to-me",null,404);o("PATCH","/operators/me/availability",object("status","UNAVAILABLE"),200);
  assertEquals(5000,a("GET","/configuration/fleet",null,200).path("maxTruckCapacityKg").asInt());d("GET","/configuration/fleet",null,403);
 }
 @Test @Order(3) void vehiclesRequestsAndCancellation() throws Exception {
  secondDriver();String plate="TST"+key().replace("-","").substring(0,7);
  ObjectNode vehicle=object("licensePlate",plate,"brand","Integration","model","Java","vehicleType","CAR","declaredWeightKg",1000);
  String vid=id(d("POST","/vehicles",vehicle,201));String vp="/vehicles/"+vid;
  d("POST","/vehicles",vehicle.deepCopy().put("licensePlate",plate.toUpperCase()),409);
  for(int weight:List.of(499,3001))d("POST","/vehicles",vehicle.deepCopy().put("declaredWeightKg",weight),400);
  d("POST","/vehicles",vehicle.deepCopy().put("ownerSubject","another"),400);a("POST","/vehicles",vehicle,403);
  d("GET",vp,null,200);d("GET","/vehicles?registrationStatus=ACTIVE&size=1",null,200);d("GET","/vehicles?size=101",null,400);
  JsonNode empty=call("OTHER_DRIVER","GET",api("/vehicles"),null,200);assertTrue(empty.path("content").isEmpty());
  JsonNode foreign=call("OTHER_DRIVER","GET",api(vp),null,404),missing=call("OTHER_DRIVER","GET",api("/vehicles/"+key()),null,404);assertEquals(foreign.path("detail"),missing.path("detail"));
  call("OTHER_DRIVER","PATCH",api(vp),object("brand","No"),404);
  d("PATCH",vp,object("declaredWeightKg",3000,"model","Updated"),200);d("PATCH",vp,object("declaredWeightKg",500),200);
  ObjectNode request=object("vehicleId",vid,"originLatitude",-31.4167,"originLongitude",-64.1833,"destinationLatitude",-31.42,"destinationLongitude",-64.19,"mobilityCondition","ROLLABLE","description","Integration request");
  d("PATCH",vp+"/registration-status",object("status","INACTIVE"),200);dk("POST","/assistance-requests",request,key(),409);
  d("PATCH",vp+"/registration-status",object("status","ACTIVE"),200);
  dk("POST","/assistance-requests",request.deepCopy().put("originLatitude",-31.1234567),key(),400);
  dk("POST","/assistance-requests",request.deepCopy().put("destinationLatitude",-31.4167).put("destinationLongitude",-64.1833),key(),400);
  dk("POST","/assistance-requests",request.deepCopy().put("status","ASSIGNED"),key(),400);d("POST","/assistance-requests",request,400);
  call("OTHER_DRIVER","POST",api("/assistance-requests"),request,key(),404);
  String creationKey=key();JsonNode created=dk("POST","/assistance-requests",request,creationKey,201);String rid=id(created),rp="/assistance-requests/"+rid;
  assertEquals("PENDING_EVALUATION",created.path("status").asText());assertFalse(created.path("latestEvaluation").isNull());assertEquals(500,created.path("declaredWeightKg").asInt());
  assertEquals(600,java.time.Duration.between(Instant.parse(created.path("createdAt").asText()),Instant.parse(created.path("searchDeadline").asText())).toSeconds());
  assertEquals("AR-MVP-5",created.path("configurationRevision").asText());assertEquals(15000,json(created.path("configurationSnapshot").asText()).path("coverage").path("radius-meters").asInt());
  assertEquals(rid,id(dk("POST","/assistance-requests",request,creationKey,200)));
  dk("POST","/assistance-requests",request.deepCopy().put("description","Changed"),creationKey,409);dk("POST","/assistance-requests",request,key(),409);
  d("PATCH",vp,object("declaredWeightKg",2000),409);d("PATCH",vp+"/registration-status",object("status","INACTIVE"),409);
  d("GET",rp,null,200);a("GET",rp,null,200);o("GET",rp,null,404);call("OTHER_DRIVER","GET",api(rp),null,404);
  d("GET","/assistance-requests?status=REQUESTED",null,200);a("GET","/assistance-requests",null,200);assertTrue(o("GET","/assistance-requests",null,200).path("content").isEmpty());
  assertEquals(2,d("GET",rp+"/history",null,200).path("totalElements").asInt());call("OTHER_DRIVER","GET",api(rp+"/history"),null,404);
  a("POST",rp+"/cancellations",object("reason","Cancel"),403);call("OTHER_DRIVER","POST",api(rp+"/cancellations"),object("reason","Cancel"),key(),404);
  String cancellationKey=key();ObjectNode cancel=object("reason","Integration finished");JsonNode event=dk("POST",rp+"/cancellations",cancel,cancellationKey,200);
  assertEquals(event,dk("POST",rp+"/cancellations",cancel,cancellationKey,200));dk("POST",rp+"/cancellations",object("reason","Other"),cancellationKey,409);
  assertEquals("CANCELLED",d("GET",rp,null,200).path("status").asText());assertEquals(3,d("GET",rp+"/history",null,200).path("totalElements").asInt());
  d("PATCH",vp,object("declaredWeightKg",2000),200);assertEquals(500,d("GET",rp,null,200).path("declaredWeightKg").asInt());
  // Real simultaneous requests: one active request per vehicle.
  try(var pool=Executors.newFixedThreadPool(2)) {
   String k1=key(),k2=key();var x=pool.submit(()->raw("DRIVER","POST",api("/assistance-requests"),request,k1));var y=pool.submit(()->raw("DRIVER","POST",api("/assistance-requests"),request,k2));
   var left=x.get();var right=y.get();assertEquals(List.of(201,409),java.util.stream.Stream.of(left.status(),right.status()).sorted().toList());
   dk("POST","/assistance-requests/"+id(left.status()==201?left.body():right.body())+"/cancellations",cancel,key(),200);
   String sharedKey=key();var first=pool.submit(()->raw("DRIVER","POST",api("/assistance-requests"),request,sharedKey));var second=pool.submit(()->raw("DRIVER","POST",api("/assistance-requests"),request,sharedKey));
   var one=first.get();var two=second.get();assertEquals(List.of(200,201),java.util.stream.Stream.of(one.status(),two.status()).sorted().toList());assertEquals(id(one.body()),id(two.body()));
   dk("POST","/assistance-requests/"+id(one.body())+"/cancellations",cancel,key(),200);
  }
  a("GET","/configuration/assistance",null,200);d("GET","/configuration/assistance",null,403);
  persistedRequestId=rid;persistedRequest=d("GET",rp,null,200);
 }
 @Test @Order(4) void requestSnapshotAndHistorySurviveRestart() throws Exception {
  assertNotNull(persistedRequestId,"Run the complete integration suite for restart verification");
  String historyPath=api("/assistance-requests/"+persistedRequestId+"/history");JsonNode before=call("DRIVER","GET",historyPath,null,200);
  String budgetBefore=routingBudgetState();
  Process restart=new ProcessBuilder("docker","compose","restart","assistance-service").directory(root.toFile()).redirectErrorStream(true).start();
  restart.getInputStream().readAllBytes();assertEquals(0,restart.waitFor());
  boolean ready=false;
  for(int attempt=0;attempt<30;attempt++) {
   try { var response=raw("DRIVER","GET",api("/assistance-requests/"+persistedRequestId),null,null);if(response.status()==200){assertEquals(persistedRequest,response.body());ready=true;break;} }
   catch(java.io.IOException ignored) { /* The application is still restarting. */ }
   Thread.sleep(1000);
  }
  assertTrue(ready,"Assistance did not become ready after restart");assertEquals(budgetBefore,routingBudgetState());assertEquals(before,call("DRIVER","GET",historyPath,null,200));
 }
 @Test @Order(5) @EnabledIfSystemProperty(named="auxiruta.realRouting",matches="true")
 void realRouteAndFleetEvaluation() throws Exception {
  assertFalse(ENV.getOrDefault("ORS_API_KEY","").isBlank(),"Configure ORS_API_KEY locally");
  String op=id(o("GET","/operators/me",null,200));
  String tid=id(a("POST","/tow-trucks",object("licensePlate","TEST"+key().replace("-","").substring(0,6),"maximumLoadKg",3000,"supportsNonRollable",true,"supportedTypes",List.of("CAR")),201));
  String rid=null;
  try {
   a("PUT","/tow-trucks/"+tid+"/operator",object("operatorId",op),200);
   call("ADMIN","PATCH",api("/tow-trucks/"+tid+"/operational-status"),object("status","ACTIVE","reason","Phase 6 real route"),key(),200);
   o("PATCH","/operators/me/availability",object("status","AVAILABLE"),200);
   o("PUT","/tow-trucks/assigned-to-me/location",object("latitude",-31.42,"longitude",-64.19),200);
   String vid=id(d("POST","/vehicles",object("licensePlate","TST"+key().replace("-","").substring(0,7),"vehicleType","CAR","declaredWeightKg",1000),201));
   ObjectNode body=object("vehicleId",vid,"originLatitude",-31.4167,"originLongitude",-64.1833,"destinationLatitude",-31.42,"destinationLongitude",-64.19,"mobilityCondition","ROLLABLE","description","Phase 6 real ORS");
   JsonNode created=dk("POST","/assistance-requests",body,key(),201);rid=id(created);
   JsonNode evaluation=created.path("latestEvaluation");
   assertEquals("VIABLE",evaluation.path("result").asText(),"Real route evaluation reason: "+evaluation.path("reasonCode").asText());
   assertEquals("PENDING_EVALUATION",created.path("status").asText());
   assertTrue(evaluation.path("transportDistanceM").asDouble()>=500);
   JsonNode candidate=evaluation.path("candidates").get(0);assertEquals(tid,candidate.path("towTruckId").asText());assertTrue(candidate.path("eligible").asBoolean());assertEquals(1,candidate.path("rankingPosition").asInt());assertTrue(candidate.path("approachDistanceM").asDouble()>0);
   assertEquals(evaluation,d("GET","/assistance-requests/"+rid,null,200).path("latestEvaluation"));
   dk("POST","/assistance-requests/"+rid+"/cancellations",object("reason","Real route checked"),key(),200);rid=null;
   JsonNode rejected=dk("POST","/assistance-requests",body.deepCopy().put("destinationLatitude",-32),key(),201);
   assertEquals("REJECTED",rejected.path("status").asText());assertEquals("OUTSIDE_COVERAGE",rejected.path("latestEvaluation").path("reasonCode").asText());
   System.out.println("Real ORS evaluation: transport="+evaluation.path("transportDistanceM").asText()+"m, approach="+candidate.path("approachDistanceM").asText()+"m, ranking=1");
  } finally {
   if(rid!=null){var current=d("GET","/assistance-requests/"+rid,null,200);if(current.path("status").asText().equals("PENDING_EVALUATION"))dk("POST","/assistance-requests/"+rid+"/cancellations",object("reason","Test cleanup"),key(),200);}
   o("PATCH","/operators/me/availability",object("status","UNAVAILABLE"),200);
   call("ADMIN","PATCH",api("/tow-trucks/"+tid+"/operational-status"),object("status","INACTIVE","reason","Test finished"),key(),200);
   a("DELETE","/tow-trucks/"+tid+"/operator",null,204);
  }
 }
 private static String routingBudgetState() throws Exception {
  Process p=new ProcessBuilder("docker","compose","exec","-T","postgres","psql","-U","assistance_user","-d","assistance_db","-Atc","select count(*),max(called_at),(select blocked_until from routing_budget where id=1) from routing_calls").directory(root.toFile()).redirectErrorStream(true).start();
  String result=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(0,p.waitFor(),"Read routing budget state");return result;
 }
}
