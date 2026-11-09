package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

/** Persistent local demonstration identities; never deleted by integration fixture cleanup. */
@EnabledIfSystemProperty(named="auxiruta.privacySeed", matches="true")
class PrivacyDemoSetupTest {
 @Test void prepareAndVerifyPrivacy() throws Exception { initialize(); prepare(); }

 static void prepare() throws Exception {
  var response=formPost("http://localhost:8080/realms/master/protocol/openid-connect/token",
   Map.of("grant_type","password","client_id","admin-cli","username","bootstrap-admin",
    "password",ENV.get("KC_BOOTSTRAP_ADMIN_PASSWORD")),null);
  assertEquals(200,response.statusCode(),"Local Keycloak administration");
  adminToken=json(response.body()).path("access_token").asText();
  ensureIdentity("driver2.demo","DRIVER");
  ensureIdentity("operator2.demo","OPERATOR");

  String secondOperator=ensureOperator("OPERATOR2","Segundo operador de demostracion");
  String firstOperator=ensureOperator("OPERATOR","Operador de demostracion AuxiRuta");
  assertNotEquals(firstOperator,secondOperator);
  assertNotEquals(claims(TOKENS.get("DRIVER")).path("sub"),claims(TOKENS.get("DRIVER2")).path("sub"));
  String first=ensureVehicle("DRIVER","DEMODRV1");
  String second=ensureVehicle("DRIVER2","DEMODRV2");
  assertNotEquals(first,second);
  call("DRIVER","GET","/api/v1/vehicles/"+first,null,200);
  call("DRIVER2","GET","/api/v1/vehicles/"+second,null,200);
  call("DRIVER","GET","/api/v1/vehicles/"+second,null,404);
  call("DRIVER2","GET","/api/v1/vehicles/"+first,null,404);
  call("DRIVER","PATCH","/api/v1/vehicles/"+second,object("brand","Forbidden"),404);
  call("DRIVER2","PATCH","/api/v1/vehicles/"+first,object("brand","Forbidden"),404);
  assertNotEquals("Forbidden",call("DRIVER","GET","/api/v1/vehicles/"+first,null,200).path("brand").asText());
  assertNotEquals("Forbidden",call("DRIVER2","GET","/api/v1/vehicles/"+second,null,200).path("brand").asText());
  assertNull(findVehicle("DRIVER","DEMODRV2"),"Foreign vehicle must not appear in owner list");
  assertNull(findVehicle("DRIVER2","DEMODRV1"),"Foreign vehicle must not appear in owner list");
  // Execute the published privacy requests with Java; Postman's scripts remain presentation helpers.
  var collection=JSON.readTree(java.nio.file.Files.readString(root.resolve("docs/api/AuxiRuta-privacidad.postman_collection.json")));
  int[] expected={200,404,404,200,404,404,200,200,200,200};
  assertEquals(expected.length,collection.path("item").size());
  int index=0;
  for(var item:collection.path("item")){
   var req=item.path("request");
   String role=req.at("/auth/bearer/0/value").asText().replace("{{","").replace("_token}}","").toUpperCase(java.util.Locale.ROOT);
   String path=req.path("url").asText().replace("{{base_url}}","")
    .replace("{{privacy_vehicle1_id}}",first).replace("{{privacy_vehicle2_id}}",second);
   JsonNode body=req.has("body")?JSON.readTree(req.at("/body/raw").asText()):null;
   call(role,req.path("method").asText(),path,body,expected[index++]);
  }
  System.out.println("Privacidad preparada: driver.demo / driver2.demo, operator.demo / operator2.demo.");
  System.out.println("Vehiculos propios: DEMODRV1="+first+"; DEMODRV2="+second);
  System.out.println("Perfiles operador: operator.demo="+firstOperator+"; operator2.demo="+secondOperator);
 }

 private static void ensureIdentity(String username,String role) throws Exception {
  var users=admin("GET","/users?username="+encode(username)+"&exact=true",null,200);
  if(users.isEmpty()){
   admin("POST","/users",object("username",username,"firstName",role,"lastName","Demo 2",
    "email",username+"@auxiruta.example","emailVerified",true,"enabled",true,
    "credentials",List.of(object("type","password","value",ENV.get("DEMO_"+role+"_PASSWORD"),"temporary",false))),201);
   users=admin("GET","/users?username="+encode(username)+"&exact=true",null,200);
  }
  assertEquals(1,users.size(),"Expected unique demo identity");
  String user=users.get(0).path("id").asText();
  var mapping=admin("GET","/roles/"+role,null,200);
  admin("POST","/users/"+user+"/role-mappings/realm",JSON.createArrayNode().add(mapping),204);
  // Preserve existing passwords; if changed manually, report login failure rather than reset them.
  String token=login(username,ENV.get("DEMO_"+role+"_PASSWORD"));
  TOKENS.put(role+"2",token);
  var roles=claims(token).at("/realm_access/roles");
  for(String business:List.of("ADMIN","DRIVER","OPERATOR","FLEET_SERVICE")){
   boolean present=false;for(var assigned:roles)if(assigned.asText().equals(business))present=true;
   assertEquals(business.equals(role),present,"Demo account must have only its intended business role");
  }
 }

 private static String ensureOperator(String tokenRole,String name) throws Exception {
  var current=raw(tokenRole,"GET","/api/v1/operators/me",null,null);
  if(current.status()==200)return id(current.body());
  assertEquals(404,current.status());
  // New profiles remain unavailable and unlinked, so they do not compete for proposals.
  return id(call("ADMIN","POST","/api/v1/operators",object("keycloakSubject",
   claims(TOKENS.get(tokenRole)).path("sub").asText(),"fullName",name),201));
 }

 private static String ensureVehicle(String role,String plate) throws Exception {
  var existing=findVehicle(role,plate);
  if(existing!=null)return id(existing);
  return id(call(role,"POST","/api/v1/vehicles",object("licensePlate",plate,
   "brand","Demo","model","Privacidad","vehicleType","CAR","declaredWeightKg",1200),201));
 }

 private static JsonNode findVehicle(String role,String plate) throws Exception {
  for(int page=0;;page++){
   var result=call(role,"GET","/api/v1/vehicles?page="+page+"&size=100",null,200);
   for(var item:result.path("content"))if(item.path("licensePlate").asText().equals(plate))return item;
   if(page+1>=result.path("totalPages").asInt())return null;
  }
 }
}
