"""Mint HS256 access tokens for seeded users with the throwaway local key. Prints JSON."""
import base64, hashlib, hmac, json, sys, time, uuid
NS = uuid.UUID("6f1c2a52-0000-4000-8000-000000000000")
key = base64.b64decode(open(sys.argv[1]).read().strip())
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def token(uid, role):
    now = int(time.time())
    header = b64(json.dumps({"alg": "HS256"}).encode())
    claims = {"iss": "meetjyou", "sub": uid, "userUuid": uid, "role": role,
              "token_type": "access", "iat": now, "exp": now + 4 * 3600}
    body = b64(json.dumps(claims).encode())
    sig = b64(hmac.new(key, f"{header}.{body}".encode(), hashlib.sha256).digest())
    return f"{header}.{body}.{sig}"
def u(kind, i): return str(uuid.uuid5(NS, f"{kind}-{i}"))
print(json.dumps({
    "admin": token(u("user", 10000), "ADMIN"),
    "hubs": [token(u("user", i), "USER") for i in range(10003, 10053)],
    "viewers": [token(u("user", i), "USER") for i in range(10101, 10601)],
    "posts": [u("post", p) for p in range(1001, 1031)],
}))
