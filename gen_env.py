#!/usr/bin/env python3
"""Generuje .env z losowymi sekretami. Uruchom raz przed startem."""
import secrets, string, os, sys

def rand_str(n=48):
    return secrets.token_urlsafe(n)

def rand_pass(n=24):
    alphabet = string.ascii_letters + string.digits + "!@#$%^&*"
    return "".join(secrets.choice(alphabet) for _ in range(n))

env_path = os.path.join(os.path.dirname(__file__), ".env")
if os.path.exists(env_path):
    ans = input(".env już istnieje. Nadpisać? [t/N] ").strip().lower()
    if ans != "t":
        print("Anulowano.")
        sys.exit(0)

with open(env_path, "w") as f:
    f.write(f"""SECRET_KEY={rand_str()}
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
REFRESH_TOKEN_EXPIRE_DAYS=7

REDIS_URL=redis://localhost:6379
REDIS_PASSWORD={rand_pass()}

MISSION_URL=http://localhost:8081
API_PORT=8000
MISSION_PORT=8081
P2P_PORT=9000

FCM_SERVER_KEY=UZUPELNIJ_Z_FIREBASE_CONSOLE
TLS_CERT_FILE=certs/server.crt
TLS_KEY_FILE=certs/server.key
""")
print(f"[OK] .env wygenerowano: {env_path}")
print("[!]  Ustaw FCM_SERVER_KEY jeśli używasz powiadomień push.")
