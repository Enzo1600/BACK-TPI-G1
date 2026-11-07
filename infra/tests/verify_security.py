"""Integration with real Keycloak tokens; requires docker compose up --build."""
import verify_identity as identity
import urllib.request as u
import urllib.error as e
import json

def check(path, status, role=None, method="GET", token=None):
    headers = {}
    if role: token = identity.tokens[role]
    if token: headers["Authorization"] = "Bearer " + token
    req = u.Request("http://localhost:8000" + path, headers=headers, method=method)
    try:
        response = u.urlopen(req, timeout=20)
    except e.HTTPError as error:
        response = error
    with response:
        body = json.load(response)
        assert response.status == status, (path, response.status, status)
        if status >= 400:
            assert response.headers.get_content_type() == "application/problem+json"
            assert body["status"] == status
        else:
            assert "openapi" in body or "content" in body
    print("OK", method, path, role or "no human role", status)

for service in ["fleet", "assistance"]:
    check("/docs/" + service + "/v3/api-docs", 401)
    for role in ["DRIVER", "OPERATOR", "ADMIN"]:
        check("/docs/" + service + "/v3/api-docs", 200, role)
check("/api/v1/operators",403,"DRIVER")
check("/api/v1/vehicles",403,"ADMIN",method="POST")
check("/api/v1/operators/me",403,"ADMIN",method="PATCH")
check("/internal/fleet/candidates",403,"ADMIN")
check("/api/v1/vehicles",404,"DRIVER") # Authorized route, controller belongs to phase 5.
check("/api/v1/operators",200,"ADMIN") # Fleet catalogue implemented in phase 4.
check("/docs/fleet/v3/api-docs",401,token=identity.technical["access_token"])
t = identity.tokens["DRIVER"].split(".")
t[2] = ("A" if t[2][0] != "A" else "B") + t[2][1:]
check("/docs/fleet/v3/api-docs",401,token=".".join(t))
print("OK security: real signature, audience, roles, routing and ProblemDetail")

# Direct calls stay inside the Docker network. Tokens travel through stdin only.
import subprocess
def direct(service, port, path, expected, token=None):
    config = 'url = "http://' + service + ':' + str(port) + path + '"\n'
    if token: config += 'header = "Authorization: Bearer ' + token + '"\n'
    result = subprocess.run(["docker","compose","exec","-T","api-gateway","curl","-sS","--max-time","10","--config","-","-w","\n%{http_code}"],input=config,text=True,capture_output=True,check=True)
    body, status = result.stdout.rsplit("\n",1)
    assert int(status) == expected, (service,path,status,expected)
    assert json.loads(body)["status"] == expected
    print("OK direct service",service,path,status)
direct("fleet-service",8081,"/internal/fleet/candidates",401)
direct("fleet-service",8081,"/internal/fleet/candidates",403,identity.tokens["ADMIN"])
direct("fleet-service",8081,"/internal/fleet/candidates",404,identity.technical["access_token"])
direct("fleet-service",8081,"/api/v1/operators",403,identity.tokens["DRIVER"])
direct("assistance-service",8082,"/api/v1/vehicles",403,identity.tokens["ADMIN"])
direct("assistance-service",8082,"/api/v1/vehicles",401,identity.technical["access_token"])
print("OK defense in depth and technical authorization on the private network")
