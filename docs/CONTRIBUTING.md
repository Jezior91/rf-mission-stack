# Contributing to RF Mission Stack

Dziękujemy za zainteresowanie projektem! 🎉

## Jak zacząć

1. Fork repozytorium
2. Sklonuj fork: `git clone https://github.com/TWOJ_LOGIN/rf-mission-stack`
3. Utwórz branch: `git checkout -b feature/moja-funkcja`
4. Zainstaluj środowisko: `python3 install_v7.py --no-start`
5. Uruchom testy: `./test.sh`

## Standardy kodu

| Język | Formatter | Linter |
|-------|-----------|--------|
| Python | `black` | `ruff` |
| Go | `gofmt` | `golangci-lint` |
| Kotlin | `ktlint` | Android Lint |
| TypeScript | `prettier` | `eslint` |

## Struktura PR

- Tytuł: `[MODUŁ] Krótki opis` np. `[CoT] Dodaj import z WinTAK`
- Opis: co, dlaczego, jak testowano
- Testy: każda nowa funkcja musi mieć testy
- Dokumentacja: zaktualizuj odpowiednie pliki docs/

## Role ETAP — kontekst

System 8 ról hierarchicznych: MET → KOR → KMB → WYK → INF → PRB → PMR → OBS
Każda rola ma inne uprawnienia API — dokumentacja w `docs/API.md`

## Zgłaszanie błędów

Użyj GitHub Issues z szablonem:
- OS + wersja Python/Go
- Kroki do reprodukcji
- Oczekiwane vs rzeczywiste zachowanie
- Logi (z `--debug`)
