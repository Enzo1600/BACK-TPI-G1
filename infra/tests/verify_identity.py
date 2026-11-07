# Prueba de integracion exclusivamente para localhost; no imprime tokens.
from pathlib import Path
import urllib.request as u, urllib.parse as p, urllib.error as e, http.cookiejar, json, base64, hashlib, secrets, re, html
root = Path(__file__).resolve().parents[2]
env = dict((line.split('=', 1) for line in (root / '.env').read_text().splitlines() if line and (not line.startswith('#'))))
base = 'http://localhost:8080/realms/auxiruta/protocol/openid-connect'

def post(url, data, opener=None):
    req = u.Request(url, p.urlencode(data).encode())
    with opener.open(req, timeout=20) if opener else u.urlopen(req, timeout=20) as r:
        return json.load(r)

def claims(token):
    part = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))

def check(token, audience, role, client):
    c = claims(token)
    assert isinstance(c.get('sub'), str) and c['sub'], 'Falta sub: aplicar infra/keycloak/ensure_basic_scope.py'
    a = c['aud']
    assert audience in (a if isinstance(a, list) else [a])
    assert c['iss'] == 'http://localhost:8080/realms/auxiruta'
    assert c['azp'] == client
    assert role in c['realm_access']['roles']
    return c
technical = post(base + '/token', {'grant_type': 'client_credentials', 'client_id': 'assistance-service', 'client_secret': env['KEYCLOAK_CLIENT_SECRET']})
c = check(technical['access_token'], 'auxiruta-fleet', 'FLEET_SERVICE', 'assistance-service')
assert not set(['DRIVER', 'OPERATOR', 'ADMIN']) & set(c['realm_access']['roles'])
print('OK token tecnico: issuer, audiencia, cliente y rol exclusivos')

class NoRedirect(u.HTTPRedirectHandler):

    def redirect_request(self, *args, **kwargs):
        return None
tokens = {}
for user, role in [('driver', 'DRIVER'), ('operator', 'OPERATOR'), ('admin', 'ADMIN')]:
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    state = secrets.token_urlsafe(16)
    op = u.build_opener(u.HTTPCookieProcessor(http.cookiejar.CookieJar(policy=http.cookiejar.DefaultCookiePolicy(secure_protocols=('https', 'wss', 'http')))), NoRedirect())
    redirect = 'http://127.0.0.1:8765/callback'
    url = base + '/auth?' + p.urlencode({'client_id': 'auxiruta-api', 'redirect_uri': redirect, 'response_type': 'code', 'scope': 'openid', 'code_challenge': challenge, 'code_challenge_method': 'S256', 'state': state})
    page = op.open(url, timeout=20).read().decode()
    action = html.unescape(re.search('<form[^>]*action="([^"]+)"', page).group(1))
    assert p.urlparse(action).netloc == 'localhost:8080'
    try:
        op.open(u.Request(action, p.urlencode({'username': user + '.demo', 'password': env['DEMO_' + role + '_PASSWORD'], 'credentialId': ''}).encode()), timeout=20)
        raise AssertionError('Login no redirigio')
    except e.HTTPError as err:
        assert err.code == 302, err.code
        q = p.parse_qs(p.urlparse(err.headers['Location']).query)
        assert q['state'] == [state]
        code = q['code'][0]
    data = post(base + '/token', {'grant_type': 'authorization_code', 'client_id': 'auxiruta-api', 'redirect_uri': redirect, 'code': code, 'code_verifier': verifier})
    tokens[role] = data['access_token']
    c = check(data['access_token'], 'auxiruta-api', role, 'auxiruta-api')
    assert 'FLEET_SERVICE' not in c['realm_access']['roles']
    print('OK Authorization Code + PKCE:', role)
try:
    post(base + '/token', {'grant_type': 'client_credentials', 'client_id': 'assistance-service', 'client_secret': 'incorrecto'})
    raise AssertionError('Se acepto secreto invalido')
except e.HTTPError as err:
    assert err.code == 401
print('OK secreto tecnico incorrecto rechazado; tokens no impresos ni guardados')
