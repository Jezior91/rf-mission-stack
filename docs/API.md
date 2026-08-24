# RF Mission Stack 6.2 — Dokumentacja API

Base URL: `http://<server>:8000`

## Autentykacja

Wszystkie endpointy (poza `/health` i `/auth/*`) wymagają nagłówka:
```
Authorization: Bearer <access_token>
```

### POST /auth/login
```json
{ "username": "admin", "password": "admin123" }
```
Odpowiedź:
```json
{ "access_token": "...", "refresh_token": "...", "role": "meta-will", "expires_in": 1800 }
```

### POST /auth/refresh
```json
{ "refresh_token": "..." }
```

## Węzły

### GET /nodes
Zwraca listę wszystkich węzłów.

### POST /nodes
```json
{ "id": "n1", "name": "Baza Alpha", "status": "online", "role": "commander", "ip": "10.0.0.1" }
```

### PATCH /nodes/{id}/status
```json
{ "status": "degraded" }
```

### DELETE /nodes/{id}
Usuwa węzeł.

## Misje (proxy → Mission Engine :8081)

### GET /missions
Lista misji posortowanych wg priorytetu.

### POST /missions
```json
{ "name": "Rekon A", "priority": 5, "nodes": ["n1","n2"], "metadata": {"area": "sector-7"} }
```

### PATCH /missions/{id}/status
```json
{ "status": "active" }
```
Statusy: `pending` | `active` | `completed` | `failed`

## WebSocket

### WS /ws
Strumieniowanie zdarzeń w czasie rzeczywistym.
```json
{ "event": "status_changed", "node_id": "n1", "status": "offline" }
{ "event": "registered",     "node_id": "n2" }
```

## Kody błędów
| Kod | Znaczenie |
|-----|-----------|
| 400 | Błędne dane wejściowe |
| 401 | Brak/nieważny token |
| 404 | Zasób nie istnieje |
| 503 | Redis niedostępny |
