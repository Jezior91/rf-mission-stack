# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 7.x     | ✅ Active support  |
| 6.x     | ⚠️ Critical fixes only |
| < 6.0   | ❌ No support      |

## Reporting a Vulnerability

Jeśli odkryjesz lukę bezpieczeństwa, **nie twórz publicznego Issue**.

Napisz bezpośrednio na: **security@rfmission.local** (docelowo po wdrożeniu)

W zgłoszeniu dołącz:
1. Opis podatności
2. Kroki do reprodukcji
3. Potencjalny wpływ
4. Sugerowane poprawki (opcjonalnie)

Odpowiedź w ciągu **48h**, poprawka w ciągu **7 dni** dla krytycznych.

## Security Features

- JWT RS256 — tokeny z podpisem asymetrycznym
- ETAP RBAC — 8 poziomów ról z hierarchią uprawnień
- Rate limiting — 100 req/min per IP
- CoT XML — walidacja schematu przed parsowaniem
- WiFi Direct — szyfrowanie na poziomie WPA2
