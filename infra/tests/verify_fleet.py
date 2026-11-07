"""Phase 4 integration against local Docker/PostgreSQL. Keeps clearly labelled test data.
Requires the demo identities. Refuses to unlink a truck not marked as a test fixture.
"""
import verify_identity as identity
import urllib.request as u
import urllib.error as e
import json, uuid
from datetime import datetime, timedelta, timezone
from concurrent.futures import ThreadPoolExecutor
BASE="http://127.0.0.1:8000/api/v1"
count=0

def request(method,path,body=None,role="ADMIN",expected=200,key=None):
 global count
 headers={"Authorization":"Bearer "+identity.tokens[role],"Content-Type":"application/json"}
 if key: headers["Idempotency-Key"]=key
 req=u.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try: response=u.urlopen(req,timeout=20)
 except e.HTTPError as error: response=error
 with response:
  raw=response.read(); data=json.loads(raw) if raw else None
  assert response.status in ([expected] if isinstance(expected,int) else expected),(method,path,response.status,expected,data)
  if response.status>=400:
   assert response.headers.get_content_type()=="application/problem+json"
   assert data["status"]==response.status
  if response.status==201: assert response.headers["Location"].startswith("/api/v1/")
  count+=1
  return response.status,data

def call(method,path,body=None,**kw): return request(method,path,body,**kw)[1]
subject=identity.claims(identity.tokens["OPERATOR"])["sub"]
status,operator=request("GET","/operators/me",role="OPERATOR",expected=[200,404])
if status==404:
 operator=call("POST","/operators",{"keycloakSubject":subject,"fullName":"Prueba automatizada Fleet","contactPhone":""},expected=201)
operator_id=operator["id"]
status,assigned=request("GET","/tow-trucks/assigned-to-me",role="OPERATOR",expected=[200,404])
if status==200:
 assert assigned["licensePlate"].startswith("TEST"),"El usuario demo tiene una grua ajena a la prueba; usar un entorno de pruebas separado"
 call("DELETE","/tow-trucks/"+assigned["id"]+"/operator",expected=204)
call("PATCH","/operators/"+operator_id+"/registration-status",{"status":"ACTIVE"})
call("PATCH","/operators/"+operator_id,{"fullName":"Prueba automatizada Fleet"})
call("POST","/operators",{"keycloakSubject":subject,"fullName":"Duplicado"},expected=409)
call("GET","/operators/"+operator_id)
call("GET","/operators?registrationStatus=ACTIVE&size=1")
call("GET","/operators?size=101",expected=400)
call("GET","/operators?size=0",expected=400)
call("GET","/operators?page=-1",expected=400)
call("GET","/operators/"+str(uuid.uuid4()),expected=404)
call("GET","/operators",role="OPERATOR",expected=403)
call("GET","/operators/me",expected=403)
call("PATCH","/operators/"+operator_id,{"fullName":"  "},expected=400)
call("PATCH","/operators/"+operator_id,{"keycloakSubject":"another"},expected=400)
now=datetime.now(timezone.utc)
qualification={"category":"LIGHT_TOWING","validFrom":(now-timedelta(days=1)).isoformat(),"validUntil":(now+timedelta(days=365)).isoformat()}
call("PUT","/operators/"+operator_id+"/qualification",qualification)
call("PUT","/operators/"+operator_id+"/qualification",dict(qualification,validUntil=qualification["validFrom"]),expected=400)
call("PUT","/operators/"+operator_id+"/qualification",dict(qualification,category="OTHER"),expected=400)
call("PATCH","/operators/me/availability",{"status":"AVAILABLE"},role="OPERATOR")
first=call("GET","/operators/me",role="OPERATOR")
second=call("PATCH","/operators/me/availability",{"status":"AVAILABLE"},role="OPERATOR")
assert first["availableSince"]==second["availableSince"]
call("PATCH","/operators/"+operator_id+"/registration-status",{"status":"INACTIVE"})
call("PATCH","/operators/me/availability",{"status":"AVAILABLE"},role="OPERATOR",expected=409)
call("PATCH","/operators/"+operator_id+"/registration-status",{"status":"ACTIVE"})
call("PATCH","/operators/me/availability",{"status":"AVAILABLE"},role="OPERATOR")
plate="TEST"+uuid.uuid4().hex[:6].upper()
body={"licensePlate":plate.lower(),"maximumLoadKg":3000,"supportsNonRollable":True,"supportedTypes":["CAR","SUV"]}
truck=call("POST","/tow-trucks",body,expected=201);truck_id=truck["id"]
assert truck["licensePlate"]==plate and truck["operationalStatus"]=="INACTIVE" and truck["operatorId"] is None
call("POST","/tow-trucks",dict(body,licensePlate=plate),expected=409)
for capacity in [499,5001]: call("POST","/tow-trucks",dict(body,maximumLoadKg=capacity),expected=400)
call("POST","/tow-trucks",dict(body,supportedTypes=[]),expected=400)
call("POST","/tow-trucks",dict(body,operationalStatus="ACTIVE"),expected=400)
call("POST","/tow-trucks",body,role="OPERATOR",expected=403)
call("GET","/tow-trucks?operationalStatus=INACTIVE&size=2")
call("GET","/tow-trucks/"+truck_id)
call("PATCH","/tow-trucks/"+truck_id,{"maximumLoadKg":5000,"supportedTypes":["CAR","SUV","PICKUP"]})
call("PATCH","/tow-trucks/"+truck_id,{"maximumLoadKg":5001},expected=400)
other=call("POST","/tow-trucks",dict(body,licensePlate="TEST"+uuid.uuid4().hex[:6]),expected=201)
def link(tid): return request("PUT","/tow-trucks/"+tid+"/operator",{"operatorId":operator_id},expected=[200,409])
with ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(link,[truck_id,other["id"]]))
assert sorted(x[0] for x in results)==[200,409],results
winner=next(x[1] for x in results if x[0]==200);tid=winner["id"]
assert call("GET","/tow-trucks/assigned-to-me",role="OPERATOR")["id"]==tid
call("PUT","/tow-trucks/"+tid+"/operator",{"operatorId":operator_id})
location=call("PUT","/tow-trucks/assigned-to-me/location",{"latitude":-31.4167,"longitude":-64.1833},role="OPERATOR")
assert location["locationUpdatedAt"] is not None
call("PUT","/tow-trucks/assigned-to-me/location",{"latitude":91,"longitude":0},role="OPERATOR",expected=400)
call("PUT","/tow-trucks/assigned-to-me/location",{"latitude":0,"longitude":0,"locationUpdatedAt":"2030-01-01T00:00:00Z"},role="OPERATOR",expected=400)
call("PUT","/tow-trucks/assigned-to-me/location",{"latitude":0,"longitude":0},expected=403)
key=str(uuid.uuid4());change={"status":"ACTIVE","reason":"Preparacion de prueba"}
event=call("PATCH","/tow-trucks/"+tid+"/operational-status",change,key=key)
assert event==call("PATCH","/tow-trucks/"+tid+"/operational-status",change,key=key)
call("PATCH","/tow-trucks/"+tid+"/operational-status",dict(change,reason="Distinto"),key=key,expected=409)
call("PATCH","/tow-trucks/"+tid+"/operational-status",change,expected=400)
call("PATCH","/tow-trucks/"+tid+"/operational-status",{"status":"MAINTENANCE","reason":""},key=str(uuid.uuid4()),expected=400)
call("PATCH","/tow-trucks/"+tid+"/operational-status",{"status":"MAINTENANCE","reason":"Prueba"},key=str(uuid.uuid4()))
assert event==call("PATCH","/tow-trucks/"+tid+"/operational-status",change,key=key)
assert call("GET","/tow-trucks/"+tid)["operationalStatus"]=="MAINTENANCE"
call("PATCH","/tow-trucks/"+tid+"/operational-status",{"status":"INACTIVE","reason":"Fin de prueba"},key=str(uuid.uuid4()))
call("DELETE","/tow-trucks/"+tid+"/operator",expected=204)
call("DELETE","/tow-trucks/"+tid+"/operator",expected=204)
call("GET","/tow-trucks/assigned-to-me",role="OPERATOR",expected=404)
call("PATCH","/operators/me/availability",{"status":"UNAVAILABLE"},role="OPERATOR")
configuration=call("GET","/configuration/fleet")
assert configuration["maxTruckCapacityKg"]==5000
call("GET","/configuration/fleet",role="DRIVER",expected=403)
print("OK Fleet:",count,"HTTP checks; concurrent exclusive link; durable replay; validation; ownership; administrative restrictions")
print("Fixtures retained as INACTIVE/unlinked trucks with TEST plate prefix. Operator remains UNAVAILABLE.")
