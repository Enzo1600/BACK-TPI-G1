"""Apply the phase 3 subject-scope fix to an already initialized local realm.
Idempotent; keeps users, credentials, roles and database contents.
"""
from pathlib import Path
import urllib.request as u
import urllib.parse as p
import json
root = Path(__file__).resolve().parents[2]
env = dict(line.split("=",1) for line in (root/".env").read_text().splitlines() if line and not line.startswith("#"))
base="http://localhost:8080"
request=u.Request(base+"/realms/master/protocol/openid-connect/token",data=p.urlencode({"grant_type":"password","client_id":"admin-cli","username":"bootstrap-admin","password":env["KC_BOOTSTRAP_ADMIN_PASSWORD"]}).encode())
with u.urlopen(request,timeout=20) as response: token=json.load(response)["access_token"]
def call(path,method="GET"):
 request=u.Request(base+"/admin/realms/auxiruta"+path,method=method,headers={"Authorization":"Bearer "+token},data=b"" if method=="PUT" else None)
 with u.urlopen(request,timeout=20) as response:
  body=response.read()
  return json.loads(body) if body else None
scope=next(s for s in call("/client-scopes") if s["name"]=="basic")
for client in ["auxiruta-api","assistance-service"]:
 c=call("/clients?clientId="+client)[0]
 call("/clients/"+c["id"]+"/default-client-scopes/"+scope["id"],"PUT")
 print("OK basic scope configured:",client)
