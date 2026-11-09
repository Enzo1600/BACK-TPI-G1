package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

@EnabledIfSystemProperty(named="auxiruta.delivery",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeliveryIntegrationTest {
 @BeforeAll static void login()throws Exception {initialize();}
 @Test @Order(1) void openApiMatchesControllersAndExport()throws Exception {
  for(String service:List.of("fleet","assistance")){
   var spec=call("ADMIN","GET","/docs/"+service+"/v3/api-docs",null,200);
   assertTrue(spec.path("openapi").asText().startsWith("3."));assertEquals("bearer",spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
   List<String> files=service.equals("fleet")?List.of("catalog/FleetController.java"):List.of("requests/RequestController.java","requests/AssignmentController.java","requests/ExecutionController.java");
   Set<String> expected=new HashSet<>();
   for(String file:files){String source=Files.readString(root.resolve(service+"-service/src/main/java/com/auxiruta/"+service+"/"+file));String prefix=file.endsWith("ExecutionController.java")?"/api/v1/assistance-requests":"/api/v1";var matcher=Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\(\"([^\"]+)\"\\)").matcher(source);while(matcher.find())expected.add(matcher.group(1).toLowerCase()+" "+prefix+matcher.group(2));}
   Set<String> actual=new HashSet<>();spec.path("paths").fields().forEachRemaining(path->{assertFalse(path.getKey().startsWith("/internal"));path.getValue().fields().forEachRemaining(method->{if(!Set.of("get","post","put","patch","delete").contains(method.getKey()))return;actual.add(method.getKey()+" "+path.getKey());assertFalse(method.getValue().path("summary").asText().isBlank());assertFalse(method.getValue().path("security").isEmpty());assertTrue(method.getValue().path("responses").has("401"));assertTrue(method.getValue().path("responses").has("403"));});});
   assertEquals(expected,actual,"OpenAPI debe documentar todos los endpoints publicos");
   if(Boolean.getBoolean("auxiruta.export"))DeliveryArtifacts.write(service+".openapi.json",spec);
   assertEquals(spec,JSON.readTree(Files.readString(root.resolve("docs/api/"+service+".openapi.json"))),"Regenerar contratos cuando cambien controladores o DTOs");
  }
 }
 @Test @Order(2) void executePublishedCollectionWithJava()throws Exception {
  String operator=DemoDataSetupTest.prepare();Map<String,String> vars=new HashMap<>();vars.put("operator_id",operator);vars.put("run_suffix",key().replace("-","").substring(0,6));vars.put("missing_id",key());vars.put("assignment_page","0");
  for(var step:DeliveryArtifacts.steps())if(step.key()!=null)vars.putIfAbsent("key_"+step.key(),key());
  JsonNode collection=JSON.readTree(Files.readString(root.resolve("docs/api/AuxiRuta.postman_collection.json")));assertEquals(DeliveryArtifacts.collection(),collection);
  List<JsonNode> items=new ArrayList<>();for(var folder:collection.path("item"))for(var item:folder.path("item"))items.add(item);
  assertEquals(DeliveryArtifacts.steps().size(),items.size());
  boolean completed=false;
  try{
   for(int index=0;index<items.size();index++){
    var step=DeliveryArtifacts.steps().get(index);var published=items.get(index).path("request");Instant deadline=Instant.now().plusSeconds(45);boolean ready=false;
    do{
     String path=resolve(published.path("url").asText().replace("{{base_url}}",""),vars);JsonNode body=published.has("body")?JSON.readTree(resolve(published.path("body").path("raw").asText(),vars)):null;
     var data=call(step.role(),published.path("method").asText(),path,body,step.key()==null?null:vars.get("key_"+step.key()),step.status());
     for(var capture:step.captures().entrySet())vars.put(capture.getKey(),data.at(capture.getValue()).asText());
     ready=true;for(var check:step.checks().entrySet())ready &= data.at(check.getKey()).asText().equals(resolve(check.getValue(),vars));
     if(step.assignment()){
      ready=false;for(var a:data.path("content"))if(a.path("requestId").asText().equals(vars.get("request_id")) && a.path("status").asText().equals("PROPOSED")){vars.put("assignment_id",id(a));ready=true;break;}
      if(!ready){int next=Integer.parseInt(vars.get("assignment_page"))+1;vars.put("assignment_page",String.valueOf(next<data.path("totalPages").asInt()?next:0));}
     }
     if(step.name().startsWith("20 ") && ready){assertEquals("VIABLE",data.path("latestEvaluation").path("result").asText());boolean route=false;for(var c:data.path("latestEvaluation").path("candidates"))route |= c.path("eligible").asBoolean() && c.path("approachDistanceM").asDouble()>0;assertTrue(route);}
     if(step.name().startsWith("40 "))for(String state:List.of("DISPATCHED","ARRIVED","VEHICLE_LOADED","IN_TRANSIT","COMPLETED")){int count=0;for(var h:data.path("content"))if(h.path("newStatus").asText().equals(state))count++;assertEquals(1,count,state);}
     if(!ready && step.poll())Thread.sleep(500);
    }while(!ready && step.poll() && Instant.now().isBefore(deadline));
    assertTrue(ready,step.name());System.out.println("Entrega verificada: "+step.name());
   }
   completed=true;
  }finally{if(!completed)cleanupOwnFixtures(vars);}
 }
 static String resolve(String text,Map<String,String> vars){for(var entry:vars.entrySet())text=text.replace("{{"+entry.getKey()+"}}",entry.getValue());assertFalse(text.contains("{{"),"Variable no resuelta");return text;}
 static void cleanupOwnFixtures(Map<String,String> vars)throws Exception {
  var fixture=new Phase7IntegrationTest();fixture.truck=vars.get("truck_id");fixture.operator=vars.get("operator_id");
  for(String name:List.of("request_id","cancel_request_id"))if(vars.containsKey(name)){
   String id=UUID.fromString(vars.get(name)).toString();var current=call("DRIVER","GET","/api/v1/assistance-requests/"+id,null,200);
   if(current.path("status").asText().equals("COMPLETED") && !current.path("resourceReleasePending").asBoolean())continue;
   Phase7IntegrationTest.fixtureSql("update assistance_requests set status=case when status='CANCELLED' then status else 'UNFULFILLED' end,closed_at=coalesce(closed_at,now()),version=version+1,closure_reason_code=coalesce(closure_reason_code,'TEST_FIXTURE_CLEANUP') where id='"+id+"' and description='Phase 10 delivery fixture'");fixture.requestIds.add(id);
  }
  fixture.cleanupFixtures();
 }
}
