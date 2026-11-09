package com.auxiruta.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

/** One definition for the executable Postman delivery and its Java verification. */
final class DeliveryArtifacts {
 static final String API="/api/v1", REQUEST=API+"/assistance-requests", VEHICLE=API+"/vehicles";
 record Step(String folder,String name,String role,String method,String path,JsonNode body,int status,String key,
             Map<String,String> captures,Map<String,String> checks,boolean poll,boolean assignment) {}
 static class Plan {
  final List<Step> steps=new ArrayList<>();String folder;
  Plan folder(String name){folder=name;return this;}
  Plan add(String name,String role,String method,String path,JsonNode body,int status){steps.add(new Step(folder,name,role,method,path,body,status,null,Map.of(),Map.of(),false,false));return this;}
  Plan amend(String key,Map<String,String> captures,Map<String,String> checks,boolean poll,boolean assignment){var s=steps.removeLast();steps.add(new Step(s.folder,s.name,s.role,s.method,s.path,s.body,s.status,key,captures,checks,poll,assignment));return this;}
  Plan key(String key){var s=steps.getLast();return amend(key,s.captures,s.checks,s.poll,s.assignment);}
  Plan capture(String key,String pointer){var s=steps.getLast();return amend(s.key,Map.of(key,pointer),s.checks,s.poll,s.assignment);}
  Plan check(String pointer,String value){var s=steps.getLast();return amend(s.key,s.captures,Map.of(pointer,value),s.poll,s.assignment);}
  Plan poll(){var s=steps.getLast();return amend(s.key,s.captures,s.checks,true,s.assignment);}
 }
 static JsonNode requestBody(boolean outside){return object("vehicleId","{{vehicle_id}}","originLatitude",-31.4167,"originLongitude",-64.1833,"destinationLatitude",outside?-32.0:-31.42,"destinationLongitude",-64.19,"mobilityCondition","ROLLABLE","description","Phase 10 delivery fixture");}
 static List<Step> steps(){
  var p=new Plan();String r=REQUEST+"/{{request_id}}",t=API+"/tow-trucks/{{truck_id}}",v=VEHICLE+"/{{vehicle_id}}";
  p.folder("01 - Seguridad y precondiciones")
   .add("01 Sin token",null,"GET",VEHICLE,null,401)
   .add("02 DRIVER sin permiso administrativo","DRIVER","GET",API+"/operators",null,403)
   .add("03 Perfil operador preparado","OPERATOR","GET",API+"/operators/me",null,200).capture("operator_id","/id")
   .add("04 Operador sin grua vinculada","OPERATOR","GET",API+"/tow-trucks/assigned-to-me",null,404)
   .add("05 Reglas normales","ADMIN","GET",API+"/configuration/assistance",null,200).check("/rules-version","AR-MVP-5");
  p.folder("02 - Recursos reproducibles")
   .add("06 Crear grua","ADMIN","POST",API+"/tow-trucks",object("licensePlate","TEST{{run_suffix}}","maximumLoadKg",3000,"supportsNonRollable",true,"supportedTypes",List.of("CAR")),201).capture("truck_id","/id")
   .add("07 Vincular operador","ADMIN","PUT",t+"/operator",object("operatorId","{{operator_id}}"),200)
   .add("08 Activar grua","ADMIN","PATCH",t+"/operational-status",object("status","ACTIVE","reason","Preparar demostracion de entrega"),200).key("active")
   .add("09 Ubicacion del recurso","OPERATOR","PUT",API+"/tow-trucks/assigned-to-me/location",object("latitude",-31.42,"longitude",-64.19),200)
   .add("10 Operador disponible","OPERATOR","PATCH",API+"/operators/me/availability",object("status","AVAILABLE"),200)
   .add("11 Crear vehiculo","DRIVER","POST",VEHICLE,object("licensePlate","TST{{run_suffix}}","vehicleType","CAR","declaredWeightKg",1000),201).capture("vehicle_id","/id")
   .add("12 Patente duplicada","DRIVER","POST",VEHICLE,object("licensePlate","TST{{run_suffix}}","vehicleType","CAR","declaredWeightKg",1000),409)
   .add("13 Peso fuera de limites","DRIVER","PATCH",v,object("declaredWeightKg",499),400)
   .add("14 Vehiculo inexistente","DRIVER","GET",VEHICLE+"/{{missing_id}}",null,404);
  p.folder("03 - Evaluacion y propuesta")
   .add("15 Destino fuera de cobertura","DRIVER","POST",REQUEST,requestBody(true),201).key("outside").check("/latestEvaluation/reasonCode","OUTSIDE_COVERAGE")
   .add("16 Solicitud viable","DRIVER","POST",REQUEST,requestBody(false),201).key("create").capture("request_id","/id")
   .add("17 Repeticion idempotente","DRIVER","POST",REQUEST,requestBody(false),200).key("create").check("/id","{{request_id}}")
   .add("18 Exclusividad del vehiculo","DRIVER","POST",REQUEST,requestBody(false),409).key("duplicate")
   .add("19 Edicion bloqueada","DRIVER","PATCH",v,object("declaredWeightKg",1100),409)
   .add("20 Esperar propuesta y rutas reales","DRIVER","GET",r,null,200).check("/status","AWAITING_ACCEPTANCE").poll();
  p.add("21 Buscar propuesta propia paginada","OPERATOR","GET",API+"/operator/assignments?size=100&page={{assignment_page}}",null,200).amend(null,Map.of(),Map.of(),true,true);
  p.folder("04 - Aceptacion y ejecucion")
   .add("22 DRIVER no acepta propuestas","DRIVER","POST",API+"/assignments/{{assignment_id}}/acceptance",null,403).key("badrole")
   .add("23 Aceptar propuesta","OPERATOR","POST",API+"/assignments/{{assignment_id}}/acceptance",null,200).key("accept")
   .add("24 Esperar confirmacion durable","DRIVER","GET",r,null,200).check("/status","ASSIGNED").poll()
   .add("25 Cancelacion posterior prohibida","DRIVER","POST",r+"/cancellations",object("reason","No permitido tras primera aceptacion"),409).key("cancel_denied")
   .add("26 Vinculo comprometido","ADMIN","DELETE",t+"/operator",null,409)
   .add("27 Llegada antes de despacho","OPERATOR","POST",r+"/arrival",null,409).key("early_arrival")
   .add("28 Ubicacion bloqueada tras aceptar","OPERATOR","PUT",API+"/tow-trucks/assigned-to-me/location",object("latitude",-31.42,"longitude",-64.19),409)
   .add("29 Despachar","OPERATOR","POST",r+"/dispatch",null,200).key("dispatch").check("/newStatus","DISPATCHED")
   .add("30 Repetir despacho sin duplicar","OPERATOR","POST",r+"/dispatch",null,200).key("dispatch").check("/newStatus","DISPATCHED")
   .add("31 Cargar antes de llegar","OPERATOR","POST",r+"/loading",null,409).key("early_load")
   .add("32 Llegada","OPERATOR","POST",r+"/arrival",null,200).key("arrival").check("/newStatus","ARRIVED")
   .add("33 Carga","OPERATOR","POST",r+"/loading",null,200).key("load").check("/newStatus","VEHICLE_LOADED")
   .add("34 Inicio de traslado","OPERATOR","POST",r+"/transport-start",null,200).key("transport").check("/newStatus","IN_TRANSIT")
   .add("35 Nota invalida","OPERATOR","POST",r+"/completion",object("note","corta"),400).key("badnote")
   .add("36 Entrega","OPERATOR","POST",r+"/completion",object("note","Vehiculo entregado en el destino acordado"),200).key("complete").check("/newStatus","COMPLETED")
   .add("37 Entrega idempotente","OPERATOR","POST",r+"/completion",object("note","Vehiculo entregado en el destino acordado"),200).key("complete").check("/newStatus","COMPLETED")
   .add("38 Esperar liberacion confirmada","DRIVER","GET",r,null,200).check("/resourceReleasePending","false").poll()
   .add("39 Servicio completado","DRIVER","GET",r,null,200).check("/status","COMPLETED")
   .add("40 Historial de etapas","DRIVER","GET",r+"/history?size=100",null,200)
   .add("41 Vehiculo editable nuevamente","DRIVER","PATCH",v,object("model","Entrega verificada"),200);
  p.folder("05 - Cancelacion y cierre de fixtures")
   .add("42 Operador no disponible","OPERATOR","PATCH",API+"/operators/me/availability",object("status","UNAVAILABLE"),200)
   .add("43 Nueva solicitud sin recurso","DRIVER","POST",REQUEST,requestBody(false),201).key("cancel_create").capture("cancel_request_id","/id")
   .add("44 Cancelar antes de aceptacion","DRIVER","POST",REQUEST+"/{{cancel_request_id}}/cancellations",object("reason","Cancelacion previa a primera aceptacion"),200).key("cancel").check("/newStatus","CANCELLED")
   .add("45 Consultar cancelada","DRIVER","GET",REQUEST+"/{{cancel_request_id}}",null,200).check("/status","CANCELLED")
   .add("46 Inactivar grua de prueba","ADMIN","PATCH",t+"/operational-status",object("status","INACTIVE","reason","Demostracion finalizada correctamente"),200).key("inactive")
   .add("47 Desvincular recurso libre","ADMIN","DELETE",t+"/operator",null,204);
  return p.steps;
 }
 static Path root(){Path p=Path.of("").toAbsolutePath();return Files.exists(p.resolve("api-gateway/pom.xml"))?p:p.getParent();}
 static void write(String file,JsonNode data)throws Exception {Path p=root().resolve("docs/api").resolve(file);Files.createDirectories(p.getParent());Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data)+"\n");}
 static ObjectNode environment(){var values=JSON.createArrayNode();for(String role:List.of("admin","driver","operator","driver2","operator2"))values.add(object("key",role+"_token","value","","type","secret","enabled",true));values.add(object("key","base_url","value","http://localhost:8000","type","default","enabled",true));return object("name","AuxiRuta local - sin secretos","values",values,"_postman_variable_scope","environment");}
 static ObjectNode collection(){
  var folders=JSON.createArrayNode();Map<String,ArrayNode> groups=new LinkedHashMap<>();
  for(var step:steps()){
   if(!groups.containsKey(step.folder)){var items=JSON.createArrayNode();groups.put(step.folder,items);}
   var headers=JSON.createArrayNode().add(object("key","Content-Type","value","application/json"));if(step.key!=null)headers.add(object("key","Idempotency-Key","value","{{key_"+step.key+"}}"));
   var request=object("method",step.method,"header",headers,"url","{{base_url}}"+step.path,"description","Rol: "+(step.role==null?"sin token":step.role)+". HTTP esperado: "+step.status+". Ejecutar la coleccion completa, en orden y con perfil normal. Los scripts solo encadenan las llamadas; la verificacion automatizada equivalente se implementa en Java.");
   request.set("auth",step.role==null?object("type","noauth"):object("type","bearer","bearer",List.of(object("key","token","value","{{"+step.role.toLowerCase()+"_token}}","type","string"))));
   if(step.body!=null)request.set("body",object("mode","raw","raw",step.body.toPrettyString().replace("\r\n","\n"),"options",object("raw",object("language","json"))));
   var events=JSON.createArrayNode();
   if(step.name.startsWith("01 "))events.add(event("prerequest",initScript()));
   events.add(event("test",testScript(step)));
   groups.get(step.folder).add(object("name",step.name,"request",request,"event",events));
  }
  groups.forEach((name,items)->folders.add(object("name",name,"item",items)));
  return object("info",object("name","AuxiRuta - Entrega MVP","description","Preparar el operador con DemoDataSetupTest. Obtener tres tokens PKCE y cargarlos en el entorno local. Ejecutar todas las carpetas en Runner, una iteracion, delay 0. Plazos normales. Detiene el recorrido ante errores; no forzar limpieza de un servicio en curso. Ver docs/ENTREGA.md.","schema","https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),"item",folders);
 }
 static JsonNode event(String listen,String script){return object("listen",listen,"script",object("type","text/javascript","exec",List.of(script.split("\n"))));}
 static String initScript(){StringBuilder s=new StringBuilder("for (const role of ['admin','operator','driver']) { if (!pm.environment.get(role+'_token')) throw new Error('Falta token '+role); }\npm.collectionVariables.set('run_suffix', pm.variables.replaceIn('{{$randomUUID}}').replace(/-/g,'').slice(0,6));\npm.collectionVariables.set('missing_id', pm.variables.replaceIn('{{$randomUUID}}'));\npm.collectionVariables.set('assignment_page','0');\n");for(String key:steps().stream().map(Step::key).filter(Objects::nonNull).distinct().toList())s.append("pm.collectionVariables.set('key_").append(key).append("',pm.variables.replaceIn('{{$randomUUID}}'));\n");s.append("for(const k of ['truck_id','vehicle_id','request_id','cancel_request_id','assignment_id','poll_start'])pm.collectionVariables.unset(k);\n");return s.toString();}
 static String testScript(Step s){
  StringBuilder code=new StringBuilder("const expected="+s.status+";\npm.test('HTTP esperado',()=>pm.response.to.have.status(expected));\nif(pm.response.code!==expected){pm.execution.setNextRequest(null);throw new Error('Respuesta inesperada: revisar recursos antes de repetir');}\nconst data=pm.response.code===204?{}:pm.response.json();\nconst at=(p)=>p.split('/').slice(1).reduce((v,k)=>v==null?undefined:v[k],data);\nlet ready=true;\n");
  if(s.status>=400)code.append("pm.test('ProblemDetail',()=>{pm.expect(pm.response.headers.get('Content-Type')).to.include('application/problem+json');pm.expect(data.status).to.eql(expected);});\n");
  for(var c:s.captures.entrySet())code.append("pm.collectionVariables.set('").append(c.getKey()).append("',at('").append(c.getValue()).append("'));\n");
  for(var c:s.checks.entrySet())code.append("ready=ready && String(at('").append(c.getKey()).append("'))===pm.variables.replaceIn('").append(c.getValue()).append("');\n");
  if(s.assignment)code.append("const a=data.content.find(a=>a.requestId===pm.collectionVariables.get('request_id') && a.status==='PROPOSED');ready=!!a;if(a){pm.collectionVariables.set('assignment_id',a.id);}else{const page=Number(pm.collectionVariables.get('assignment_page'));pm.collectionVariables.set('assignment_page',page+1<data.totalPages?page+1:0);}\n");
  if(s.poll)code.append("if(!ready){const start=Number(pm.collectionVariables.get('poll_start')||Date.now());pm.collectionVariables.set('poll_start',start);if(Date.now()-start>45000){pm.execution.setNextRequest(null);pm.test('Resultado dentro de 45s',()=>pm.expect(ready).to.eql(true));}else{pm.execution.setNextRequest(pm.info.requestId);setTimeout(()=>{},500);}}else{pm.collectionVariables.unset('poll_start');pm.test('Resultado observable',()=>pm.expect(ready).to.eql(true));}\n");
  else code.append("pm.test('Resultado observable',()=>pm.expect(ready).to.eql(true));if(!ready)pm.execution.setNextRequest(null);\n");
  if(s.name.startsWith("20 "))code.append("if(ready)pm.test('Evaluacion vial real',()=>{pm.expect(data.latestEvaluation.result).to.eql('VIABLE');pm.expect(data.latestEvaluation.candidates.some(c=>c.eligible && c.approachDistanceM>0)).to.eql(true);});\n");
  if(s.name.startsWith("40 "))code.append("pm.test('Una transicion por etapa',()=>{for(const state of ['DISPATCHED','ARRIVED','VEHICLE_LOADED','IN_TRANSIT','COMPLETED'])pm.expect(data.content.filter(h=>h.newStatus===state).length).to.eql(1);});\n");
  return "(() => {\n"+code+"\n})();";
 }
}
