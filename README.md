<div align="center">

# 📡 RF Mission Stack

**System zarządzania misjami P2P — Go + FastAPI + Android + CoT/ATAK**

[![CI](https://github.com/YOUR_USER/rf-mission-stack/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USER/rf-mission-stack/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-7.0-blue.svg)](CHANGELOG.md)
[![CoT Compatible](https://img.shields.io/badge/CoT-ATAK%20compatible-green.svg)](#cot--atak-compatibility)

[🇵🇱 Polski](#pl) · [🇬🇧 English](#en)

</div>

---

<a id="pl"></a>
## 🇵🇱 Polski

RF Mission Stack to otwarty system zarządzania operacjami terenowymi dla małych zespołów (5–50 osób). Działa bez specjalistycznego sprzętu, jest kompatybilny z ekosystemem ATAK i oferuje zaawansowany system ról ETAP.

### ✨ Przewagi nad konkurencją

| Feature | RF Mission Stack | FreeTAKServer | OpenTAKServer | Meshtastic |
|---------|:---:|:---:|:---:|:---:|
| Backend Go (libp2p) | ✅ | ❌ | ❌ | ❌ |
| Natywny P2P (bez serwera) | ✅ | ❌ | ❌ | ✅ |
| CoT / ATAK kompatybilny | ✅ **v7.0** | ✅ | ✅ | ❌ |
| System ról ETAP (8 ról) | ✅ | ❌ | ❌ | ❌ |
| Dashboard z topografią | ✅ | ❌ | ⚠️ | ❌ |
| Android offline mesh | 🔜 v7.1 | ❌ | ❌ | ✅ |
| Bez sprzętu LoRa | ✅ | ✅ | ✅ | ❌ |

### 🏗️ Architektura

```
┌─────────────────────────────────────────────┐
│              RF Mission Dashboard            │
│          (React/TSX · Topografia)            │
└─────────────────┬───────────────────────────┘
                  │ REST / WebSocket
┌─────────────────▼───────────────────────────┐
│          FastAPI Gateway (Python 3.12)        │
│   • JWT Auth · ETAP Roles · Rate Limiting     │
│   • CoT XML Import/Export (ATAK compat)       │
└──────┬──────────────────────────┬────────────┘
       │                          │
┌──────▼──────┐           ┌───────▼──────┐
│ Go Mission  │           │  Go P2P Node  │
│   Engine    │◄──Redis──►│  (libp2p)     │
│  (port 8081)│           │  (port 9000)  │
└─────────────┘           └───────────────┘
       │                          │
┌──────▼──────────────────────────▼──────────┐
│              Redis 7 (pub/sub + cache)       │
└─────────────────────────────────────────────┘
       │
┌──────▼──────────────────────────────────────┐
│          Android App (Kotlin/Compose)         │
│   • Online mode (API)                         │
│   • Offline mesh (WiFi Direct) → v7.1         │
└─────────────────────────────────────────────┘
```

### 🔑 System ról ETAP

| Kod | Rola | Uprawnienia |
|-----|------|------------|
| `MET` | 🛡️ Meta-Wola | Pełny dostęp |
| `KOR` | ⚡ Koordynator | Zarządzanie misjami |
| `WYK` | 🔑 Wykonawca | Wykonywanie zadań |
| `INF` | 🎯 Infiltrator | Rozpoznanie |
| `PRB` | 🔄 Próbobiorca | Testowanie |
| `PMR` | 📏 Pomiarowiec | Pomiary |
| `KMB` | 🎲 Kombinator | Łączenie zasobów |
| `OBS` | 👁️ Obserwator | Tylko odczyt |

### ⚡ Szybki start

```bash
# Klonuj
git clone https://github.com/YOUR_USER/rf-mission-stack.git
cd rf-mission-stack

# Zainstaluj (Windows/macOS/Linux/Termux)
python install.py

# Lub Docker
cd project
cp .env.example .env
python gen_env.py
docker compose up -d
```

### 🔗 CoT / ATAK Compatibility

```bash
# Eksport węzłów do ATAK
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/cot/nodes

# Import węzła z ATAK/WinTAK
curl -X POST -H "Content-Type: application/xml" \
     -H "Authorization: Bearer $TOKEN" \
     --data @node.cot.xml \
     http://localhost:8000/cot/import
```

---

<a id="en"></a>
## 🇬🇧 English

RF Mission Stack is an open-source field operations management system for small teams (5–50 people). No specialized hardware required, ATAK ecosystem compatible, with advanced ETAP role system.

### ⚡ Quick Start

```bash
git clone https://github.com/YOUR_USER/rf-mission-stack.git
cd rf-mission-stack
python install.py        # Windows/macOS/Linux/Android Termux
```

### 🔗 ATAK Integration

RF Mission Stack v7.0 supports full bidirectional CoT XML exchange:

```bash
# Export all nodes as CoT XML (importable to ATAK/WinTAK/iTAK)
GET /cot/nodes          → CoT XML feed
GET /cot/nodes/{id}     → single node CoT XML  
GET /cot/missions       → missions as CoT tasks

# Import from ATAK
POST /cot/import        ← CoT XML body
```

### 📱 Android Offline Mesh (v7.1 roadmap)

Android↔Android communication without internet, without AP, without LoRa hardware:
- WiFi Direct transport (~100m range)
- libp2p protocol (same as desktop nodes)
- CRDT mission state sync
- Auto-sync to server when connectivity restored

See [android/OFFLINE_MESH_SPEC.md](android/OFFLINE_MESH_SPEC.md) for full spec.

### 🧪 Running Tests

```bash
cd project
# Python
pip install pytest pytest-asyncio
pytest api/tests/ -v

# Go
cd p2p && go test ./... -v
cd mission && go test ./... -v
```

### 📄 License

MIT License — see [LICENSE](LICENSE)

### 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Commit: `git commit -m 'feat: add my feature'`
4. Push: `git push origin feature/my-feature`
5. Open Pull Request

Issues and PRs welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).
