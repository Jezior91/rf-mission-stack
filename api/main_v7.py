"""RF Mission Stack 7.0 — FastAPI Gateway z CoT + Rate Limiting"""
import os, sys, json, time, asyncio, logging
from pathlib import Path
from typing import Optional, List
from contextlib import asynccontextmanager

REQUIRED_ENV = ["SECRET_KEY", "REDIS_URL", "JWT_ALGORITHM"]
missing = [k for k in REQUIRED_ENV if not os.getenv(k)]
if missing:
    print(f"[FATAL] Brakujące zmienne środowiskowe: {missing}")
    print("        Uruchom: python gen_env.py  a następnie: source .env")
    sys.exit(1)

import redis.asyncio as aioredis
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Depends, HTTPException, status, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, validator
import httpx
from auth.auth import verify_token, TokenData
from api.cot import node_to_cot, cot_to_node, mission_to_cot
from api.middleware import RateLimitMiddleware

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("rfmission")

REDIS_URL   = os.environ["REDIS_URL"]
MISSION_URL = os.getenv("MISSION_URL", "http://mission:8081")
security    = HTTPBearer()

redis_pool: aioredis.Redis = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global redis_pool
    redis_pool = aioredis.from_url(REDIS_URL, decode_responses=True,
                                   password=os.getenv("REDIS_PASSWORD"))
    try:
        await redis_pool.ping()
        logger.info("Redis connected")
    except Exception as e:
        logger.error(f"Redis connection failed: {e}")
        sys.exit(1)
    yield
    await redis_pool.close()

app = FastAPI(title="RF Mission Stack", version="7.0", lifespan=lifespan,
              description="P2P Mission Control with CoT/ATAK compatibility")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.add_middleware(RateLimitMiddleware)

# ── ETAP role validation ──────────────────────────────────────────────────────
ETAP_ROLES = {
    "Meta-Wola", "Koordynator", "Wykonawca", "Infiltrator",
    "Próbobiorca", "Pomiarowiec", "Kombinator", "Obserwator",
    # legacy compat
    "observer", "operator", "commander", "meta-will",
}

class NodeModel(BaseModel):
    id: str
    name: str
    status: str = "offline"
    role: str = "Obserwator"
    ip: Optional[str] = None
    lat: Optional[float] = None
    lon: Optional[float] = None
    hae: Optional[float] = None
    last_seen: Optional[int] = None

    @validator("status")
    def valid_status(cls, v):
        if v not in ("online", "offline", "degraded"):
            raise ValueError("status must be online/offline/degraded")
        return v

    @validator("role")
    def valid_role(cls, v):
        if v not in ETAP_ROLES:
            raise ValueError(f"role must be one of {ETAP_ROLES}")
        return v

class NodeStatusUpdate(BaseModel):
    status: str
    @validator("status")
    def valid_status(cls, v):
        if v not in ("online", "offline", "degraded"):
            raise ValueError("status must be online/offline/degraded")
        return v

# ── Auth ──────────────────────────────────────────────────────────────────────
async def get_current_user(creds: HTTPAuthorizationCredentials = Depends(security)) -> TokenData:
    token_data = verify_token(creds.credentials)
    if token_data is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    return token_data

# ── Health ────────────────────────────────────────────────────────────────────
@app.get("/health")
async def health():
    try:
        await redis_pool.ping()
        return {"status": "healthy", "ts": int(time.time()), "version": "7.0",
                "features": ["cot", "rate_limit", "etap_v2", "p2p"]}
    except Exception:
        raise HTTPException(503, "Redis unavailable")

# ── Nodes ─────────────────────────────────────────────────────────────────────
@app.get("/nodes")
async def list_nodes(user: TokenData = Depends(get_current_user)):
    keys = await redis_pool.keys("node:*")
    nodes = []
    for k in keys:
        raw = await redis_pool.get(k)
        if raw:
            nodes.append(json.loads(raw))
    return nodes

@app.post("/nodes", status_code=201)
async def register_node(node: NodeModel, user: TokenData = Depends(get_current_user)):
    node.last_seen = int(time.time())
    await redis_pool.set(f"node:{node.id}", node.json(), ex=86400)
    await redis_pool.publish("node:events", json.dumps({"event": "registered", "node_id": node.id}))
    logger.info(f"Node registered: {node.id} by {user.sub}")
    return node

@app.patch("/nodes/{node_id}/status")
async def update_node_status(node_id: str, update: NodeStatusUpdate, user: TokenData = Depends(get_current_user)):
    raw = await redis_pool.get(f"node:{node_id}")
    if not raw:
        raise HTTPException(404, "Node not found")
    data = json.loads(raw)
    data["status"] = update.status
    data["last_seen"] = int(time.time())
    await redis_pool.set(f"node:{node_id}", json.dumps(data), ex=86400)
    await redis_pool.publish("node:events", json.dumps({"event": "status_changed", "node_id": node_id, "status": update.status}))
    return data

@app.delete("/nodes/{node_id}", status_code=204)
async def remove_node(node_id: str, user: TokenData = Depends(get_current_user)):
    if not await redis_pool.delete(f"node:{node_id}"):
        raise HTTPException(404, "Node not found")

# ── CoT / ATAK Bridge ─────────────────────────────────────────────────────────
@app.get("/cot/nodes", response_class=PlainTextResponse,
         summary="Eksport węzłów jako CoT XML (kompatybilny z ATAK/WinTAK/FreeTAKServer)")
async def export_nodes_cot(user: TokenData = Depends(get_current_user)):
    """
    Zwraca wszystkie węzły sieci jako strumień CoT XML.
    Każdy węzeł to osobny blok <event>...</event>.
    Importowalny bezpośrednio do ATAK/WinTAK przez TAK Server feed.
    """
    keys = await redis_pool.keys("node:*")
    parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<rfmission_feed version="7.0">']
    for k in keys:
        raw = await redis_pool.get(k)
        if raw:
            node = json.loads(raw)
            cot = node_to_cot(node)
            # Wytnij deklarację XML z zagnieżdżonego elementu
            cot_clean = "\n".join(line for line in cot.split("\n")
                                  if not line.startswith("<?xml"))
            parts.append(cot_clean.strip())
    parts.append("</rfmission_feed>")
    return "\n".join(parts)

@app.get("/cot/nodes/{node_id}", response_class=PlainTextResponse,
         summary="Eksport pojedynczego węzła jako CoT XML")
async def export_node_cot(node_id: str, user: TokenData = Depends(get_current_user)):
    raw = await redis_pool.get(f"node:{node_id}")
    if not raw:
        raise HTTPException(404, "Node not found")
    return node_to_cot(json.loads(raw))

@app.post("/cot/import", status_code=201,
          summary="Import węzła z CoT XML (z ATAK/WinTAK/FreeTAKServer)")
async def import_cot_node(request: Request, user: TokenData = Depends(get_current_user)):
    """
    Przyjmuje CoT XML (Content-Type: application/xml lub text/xml).
    Konwertuje do węzła RF Mission Stack i zapisuje w Redis.
    Umożliwia integrację z istniejącą infrastrukturą ATAK.
    """
    body = await request.body()
    xml_str = body.decode("utf-8")
    node = cot_to_node(xml_str)
    if node is None:
        raise HTTPException(400, "Invalid CoT XML")
    await redis_pool.set(f"node:{node['id']}", json.dumps(node), ex=86400)
    await redis_pool.publish("node:events", json.dumps({"event": "cot_import", "node_id": node["id"]}))
    logger.info(f"CoT import: node {node['id']} ({node['name']}) by {user.sub}")
    return node

@app.get("/cot/missions", response_class=PlainTextResponse,
         summary="Eksport misji jako CoT XML")
async def export_missions_cot(user: TokenData = Depends(get_current_user)):
    async with httpx.AsyncClient() as client:
        r = await client.get(f"{MISSION_URL}/missions", timeout=5)
        missions = r.json()
    parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<rfmission_tasks version="7.0">']
    for m in missions:
        cot = mission_to_cot(m)
        cot_clean = "\n".join(line for line in cot.split("\n")
                              if not line.startswith("<?xml"))
        parts.append(cot_clean.strip())
    parts.append("</rfmission_tasks>")
    return "\n".join(parts)

# ── Mission proxy ─────────────────────────────────────────────────────────────
@app.get("/missions")
async def get_missions(user: TokenData = Depends(get_current_user)):
    async with httpx.AsyncClient() as client:
        r = await client.get(f"{MISSION_URL}/missions", timeout=5)
        return r.json()

@app.post("/missions", status_code=201)
async def create_mission(payload: dict, user: TokenData = Depends(get_current_user)):
    async with httpx.AsyncClient() as client:
        r = await client.post(f"{MISSION_URL}/missions", json=payload, timeout=5)
        if r.status_code >= 400:
            raise HTTPException(r.status_code, r.text)
        return r.json()

@app.patch("/missions/{mission_id}/status")
async def update_mission_status(mission_id: str, payload: dict, user: TokenData = Depends(get_current_user)):
    async with httpx.AsyncClient() as client:
        r = await client.patch(f"{MISSION_URL}/missions/{mission_id}/status", json=payload, timeout=5)
        return r.json()

# ── WebSocket ─────────────────────────────────────────────────────────────────
connected_ws: List[WebSocket] = []

@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    connected_ws.append(ws)
    logger.info(f"WS connected, total: {len(connected_ws)}")
    pubsub = redis_pool.pubsub()
    await pubsub.subscribe("node:events", "mission:updates")
    try:
        async def redis_reader():
            async for message in pubsub.listen():
                if message["type"] == "message":
                    await ws.send_text(message["data"])
        reader_task = asyncio.create_task(redis_reader())
        while True:
            data = await ws.receive_text()
            logger.debug(f"WS recv: {data}")
    except WebSocketDisconnect:
        reader_task.cancel()
        connected_ws.remove(ws)
        await pubsub.unsubscribe()
        logger.info(f"WS disconnected, total: {len(connected_ws)}")
