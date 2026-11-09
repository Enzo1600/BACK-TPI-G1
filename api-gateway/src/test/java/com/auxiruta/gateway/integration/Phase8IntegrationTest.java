package com.auxiruta.gateway.integration;
import java.util.*;
import java.time.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;
@EnabledIfSystemProperty(named="auxiruta.phase8",matches="true")
class Phase8IntegrationTest {
 Phase7IntegrationTest fixture=new Phase7IntegrationTest();String request,assignment,vehicle;boolean fleetStopped;
 @BeforeAll static void initializeClient()throws Exception {
  initialize();secondDriver();var role=admin("GET","/roles/OPERATOR",null,200);admin("POST","/users/"+extraUserId+"/role-mappings/realm",JSON.createArrayNode().add(role),204);
  String password=key()+"Aa1!",username=admin("GET","/users/"+extraUserId,null,200).path("username").asText();admin("PUT","/users/"+extraUserId+"/reset-password",object("type","password","value",password,"temporary",false),204);TOKENS.put("OTHER_OPERATOR",login(username,password));
 }
 @AfterAll static void removeTemporaryIdentity()throws Exception {BackendClient.cleanup();}
 @BeforeEach void setup()throws Exception {fixture.resources();var r=fixture.request();request=id(r);vehicle=r.path("vehicleId").asText();assignment=id(fixture.proposal(request));call("OPERATOR","POST","/api/v1/assignments/"+assignment+"/acceptance",null,key(),200);}
 String path(String action){return "/api/v1/assistance-requests/"+request+"/"+action;}
 JsonNode action(String name,String operation,JsonNode body,int expected)throws Exception {return call("OPERATOR","POST",path(name),body,operation,expected);}
 void toTransit()throws Exception {for(String step:List.of("dispatch","arrival","loading","transport-start"))action(step,key(),null,200);}
 @Test void completesServiceAndPreservesIdempotentHistory()throws Exception {
  call("ADMIN","POST",path("dispatch"),null,key(),403);call("DRIVER","POST",path("dispatch"),null,key(),403);
  call("OTHER_OPERATOR","POST",path("dispatch"),null,key(),404);
  action("arrival",key(),null,409);action("loading",key(),null,409);
  String dispatchKey=key();var dispatch=action("dispatch",dispatchKey,null,200);assertEquals("DISPATCHED",dispatch.path("newStatus").asText());assertTrue(dispatch.path("details").asText().contains("transportDistanceM"));
  assertEquals(dispatch,action("dispatch",dispatchKey,null,200));action("arrival",dispatchKey,null,409);
  call("DRIVER","POST",path("cancellations"),object("reason","Cannot cancel after dispatch"),key(),409);
  action("transport-start",key(),null,409);action("arrival",key(),null,200);action("loading",key(),null,200);action("transport-start",key(),null,200);
  String finishKey=key();action("completion",finishKey,object("note","short"),400);
  var note=object("note","Vehicle delivered at the requested destination");var done=action("completion",finishKey,note,200);assertEquals("COMPLETED",done.path("newStatus").asText());assertEquals(done,action("completion",finishKey,note,200));action("completion",finishKey,object("note","Different completion description"),409);
  var current=call("DRIVER","GET","/api/v1/assistance-requests/"+request,null,200);assertEquals("COMPLETED",current.path("status").asText());assertFalse(current.path("resourceReleasePending").asBoolean());assertFalse(current.path("closedAt").isNull());
  assertEquals("RELEASED",Phase7IntegrationTest.internal("GET",assignment,null).path("status").asText());
  assertEquals("ACTIVE",call("ADMIN","GET","/api/v1/tow-trucks/"+fixture.truck,null,200).path("operationalStatus").asText());
  call("DRIVER","PATCH","/api/v1/vehicles/"+vehicle,object("model","Editable after completion"),200);
  var history=call("DRIVER","GET",path("history"),null,200).path("content");for(String state:List.of("DISPATCHED","ARRIVED","VEHICLE_LOADED","IN_TRANSIT","COMPLETED")){int found=0;for(var h:history)if(h.path("newStatus").asText().equals(state))found++;assertEquals(1,found);}
  fixture.requestIds.remove(request); // Preserve the normally completed request without fixture closure.
 }
 @Test void expiredDispatchIsRejected()throws Exception {
  call("OPERATOR","PUT","/api/v1/tow-trucks/assigned-to-me/location",object("latitude",-31.4167,"longitude",-64.1833),409);
  sql("assistance_user","assistance_db","update assignments set accepted_at=now()-interval '301 seconds' where id='"+UUID.fromString(assignment)+"'");action("dispatch",key(),null,409);
  assertNotEquals("DISPATCHED",call("DRIVER","GET","/api/v1/assistance-requests/"+request,null,200).path("status").asText());
 }
 @Test void completionKeepsPendingReleaseAcrossFleetOutage()throws Exception {
  toTransit();container("stop","fleet-service");fleetStopped=true;
  String operation=key();var note=object("note","Delivery completed while Fleet is unavailable");var event=action("completion",operation,note,200);
  var pending=call("DRIVER","GET","/api/v1/assistance-requests/"+request,null,200);assertEquals("COMPLETED",pending.path("status").asText());assertTrue(pending.path("resourceReleasePending").asBoolean());
  call("DRIVER","POST","/api/v1/assistance-requests",object("vehicleId",vehicle,"originLatitude",-31.4167,"originLongitude",-64.1833,"destinationLatitude",-31.42,"destinationLongitude",-64.19,"mobilityCondition","ROLLABLE","description","Must wait for resource release"),key(),409);
  startFleet();assertEquals(event,action("completion",operation,note,200));
  for(int i=0;i<50 && call("DRIVER","GET","/api/v1/assistance-requests/"+request,null,200).path("resourceReleasePending").asBoolean();i++)Thread.sleep(1000);
  assertFalse(call("DRIVER","GET","/api/v1/assistance-requests/"+request,null,200).path("resourceReleasePending").asBoolean());
  assertEquals("RELEASED",Phase7IntegrationTest.internal("GET",assignment,null).path("status").asText());fixture.requestIds.remove(request);
 }
 static void sql(String user,String database,String statement)throws Exception {Process p=new ProcessBuilder("docker","compose","exec","-T","postgres","psql","-U",user,"-d",database,"-v","ON_ERROR_STOP=1","-c",statement).directory(root.toFile()).redirectErrorStream(true).start();p.getInputStream().readAllBytes();assertEquals(0,p.waitFor());}
 static void container(String operation,String service)throws Exception {Process p=new ProcessBuilder("docker","compose",operation,service).directory(root.toFile()).redirectErrorStream(true).start();p.getInputStream().readAllBytes();assertEquals(0,p.waitFor());}
 void startFleet()throws Exception {container("start","fleet-service");for(int i=0;i<30;i++){try{if(raw("OPERATOR","GET","/api/v1/tow-trucks/assigned-to-me",null,null).status()==200){fleetStopped=false;return;}}catch(java.io.IOException ignored){}Thread.sleep(1000);}fail("Fleet did not restart");}
 @AfterEach void cleanup()throws Exception {if(fleetStopped)startFleet();fixture.cleanupFixtures();}
}
