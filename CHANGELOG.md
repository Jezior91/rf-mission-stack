# Changelog

All notable changes to RF Mission Stack are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [7.0.0] — 2026-07-14

### Added
- **CoT XML Bridge** (`api/cot.py`) — pełna kompatybilność z ATAK/WinTAK/iTAK
  - `POST /api/cot/import` — import węzłów z XML CoT
  - `GET /api/nodes/{id}/cot` — eksport węzła do CoT XML
  - `GET /api/missions/{id}/cot` — eksport misji do CoT XML
  - Mapowanie 8 ról ETAP → typy CoT (`a-f-G-U-C`, `a-n-G-E-S` itp.)
- **Android WiFi Direct Mesh** — tryb offline Android↔Android bez internetu
  - `WifiDirectManager.kt` — wykrywanie i łączenie urządzeń P2P
  - `MeshPacket.kt` — protokół pakietów z serializacją JSON
  - `MeshService.kt` — serwis tła: tryby MASTER/RELAY/LEAF
  - `MeshViewModel.kt` — LiveData i zarządzanie stanem
  - `MeshScreen.kt` — ekran Compose z listą urządzeń i statusem mesh
- **Rate Limiting** (`api/middleware.py`) — 100 req/min per IP, security headers
- **Testy jednostkowe** — 17 testów CoT (Python), testy Go P2P
- **CI/CD** — GitHub Actions: lint + test + docker build
- **README.md** — profesjonalna dokumentacja PL/EN z badges i architekturą
- **Instalator uniwersalny** (`install_v7.py`) — Windows/macOS/Linux/Termux, 1 plik, 35 plików osadzonych

### Changed
- `api/main.py` → `api/main_v7.py` — integracja CoT + middleware
- NetworkMap: 4 tryby topograficzne (Siatka/Teren/Warstwice/Satelita)
- System ETAP: pełne 8 ról (MET/KOR/WYK/INF/PRB/PMR/KMB/OBS)

### Fixed
- Pętla nieskończona `fetchSysInfo` — naprawiona przez `useRef` + interwał 60s
- Instalator Windows: usunięto `chcp 65001` psujące `set /p`/`pause`

---

## [6.2.0] — 2026-06-08

### Added
- Backend Go (libp2p P2P + Mission Engine Redis)
- API Python (FastAPI + JWT + role ETAP)
- Dashboard HTML5 interaktywny
- Android Kotlin/Compose (podstawowy)

---

## [6.0.0] — 2026-06-06

### Added
- Pierwsza wersja publiczna
- P2P Go, API FastAPI, auth JWT
