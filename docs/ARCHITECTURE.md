# RF Mission Stack — Architektura systemu

```
┌─────────────────────────────────────────────────────────┐
│                    KLIENTY                              │
│  Browser Dashboard  │  Android App  │  ATAK/WinTAK      │
│  (React/TSX)        │  (Kotlin)     │  (CoT XML)         │
└────────┬────────────┴──────┬────────┴──────┬────────────┘
         │                   │               │
         │        HTTPS/WSS  │               │ CoT XML
         ▼                   ▼               ▼
┌─────────────────────────────────────────────────────────┐
│               FastAPI Gateway (Python)                   │
│  JWT Auth │ ETAP RBAC │ Rate Limit │ CoT Bridge          │
│  /api/*   │ /ws/*     │ middleware  │ /api/cot/*          │
└─────────────────────────┬───────────────────────────────┘
                          │ gRPC / HTTP
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ P2P Engine   │  │Mission Engine│  │  Redis Cache │
│ (Go/libp2p)  │  │  (Go)        │  │              │
│              │  │              │  │ • Nodes       │
│ • DHT disco  │  │ • CRUD mis.  │  │ • Sessions   │
│ • PubSub     │  │ • Statusy    │  │ • Rate limit │
│ • NAT trav.  │  │ • Alerty     │  └──────────────┘
└──────┬───────┘  └──────────────┘
       │
       ▼
┌─────────────────────┐
│  Internet / Sieć    │
│  lub                │
│  WiFi Direct Mesh   │◄── Android↔Android offline
│  (bez AP, bez sieci)│
└─────────────────────┘
```

## Komponenty

### P2P Engine (`p2p/`)
- Protokół: libp2p (Go)
- Odkrywanie węzłów: mDNS + DHT Kademlia
- Wiadomości: PubSub gossipsub
- NAT traversal: STUN/TURN

### Mission Engine (`mission/`)
- Zarządzanie misjami operacyjnymi
- CRUD z persystencją Redis
- Eventy real-time przez WebSocket

### FastAPI Gateway (`api/`)
- Autoryzacja: JWT RS256
- Role: ETAP 8-poziomowy RBAC
- CoT Bridge: import/eksport XML (ATAK-kompatybilny)
- Rate limiting: 100 req/min/IP

### Android App (`android/`)
- UI: Jetpack Compose
- Sieć: Retrofit + OkHttp WebSocket
- Mesh offline: WiFi Direct P2P
  - Tryby: MASTER / RELAY / LEAF
  - Protokół: JSON pakiety, kompresja LZ4

### Dashboard (`apps/rf-mission-dashboard/`)
- Framework: React/TSX
- Mapa: 4 tryby (Siatka/Teren/Warstwice/Satelita)
- Real-time: WebSocket
- Panele: Nodes, Missions, Metrics, Console, Logs, Alerts
