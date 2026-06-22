<!-- IMPL-REVIEW-REPORT -->
# Przegląd implementacji: Testy integracyjne backendu

- **Plan**: context/changes/integration-tests/plan.md
- **Zakres**: wszystkie 5 faz
- **Data**: 2026-06-22
- **Werdykt**: ZAAKCEPTOWANY
- **Ustalenia**: 0 krytycznych, 0 ostrzeżeń, 2 obserwacje

## Werdykty

| Wymiar | Werdykt |
|-----------|---------|
| Zgodność z planem | PASS |
| Dyscyplina zakresu | PASS |
| Bezpieczeństwo i jakość | PASS |
| Architektura | PASS |
| Spójność wzorców | PASS |
| Kryteria sukcesu | PASS |

Refaktor potwierdzony jako behawioralnie równoważny (porównanie z `6a994e5`: te same trasy, middleware, app_data, env, migracje). Testy deploy bezpieczne (nigdy happy-path). Sekrety wyłącznie testowe. Pokrycie ≥2 testy/endpoint. Dwie zaakceptowane dewiacje (usunięty feature-gate; układ `tests/<name>/mod.rs`) i jeden uzasadniony EXTRA (`ctor::dtor` do sprzątania kontenera). Kryteria: 47 testów ✓, clippy ✓, `cargo build` bez testcontainers ✓.

## Ustalenia

### F1 — Rozmiar puli per-test vs Postgres max_connections

- **Ważność**: 🔭 OBSERWACJA
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka oczywista i wąska
- **Wymiar**: Bezpieczeństwo i jakość (Reliability)
- **Lokalizacja**: apps/backend/tests/common/mod.rs:125
- **Szczegóły**: Każdy test tworzy pulę `max_connections(5)`. Przy ~30 testach i pełnej równoległości teoretyczny szczyt 150 > domyślne Postgres `max_connections=100`. Połączenia sqlx leniwe, więc w praktyce raczej nie wyczerpie, ale to realne ryzyko.
- **Poprawka**: obniż per-test pool do `max_connections(3)`.
- **Decyzja**: SKIPPED

### F2 — Sprzątanie kontenera zależy od dtor (nie odpala się przy kill)

- **Ważność**: 🔭 OBSERWACJA
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka oczywista i wąska
- **Wymiar**: Bezpieczeństwo i jakość (Reliability)
- **Lokalizacja**: apps/backend/tests/common/mod.rs:37
- **Szczegóły**: `#[ctor::dtor]` + `docker rm -f` odpala się na normalnym wyjściu, ale nie przy SIGKILL/abort (brak reapera Ryuk). Przerwany bieg (Ctrl-C) zostawia kontener.
- **Poprawka**: dopisz w `tests/README.md` notatkę o czyszczeniu po przerwanym biegu (`docker ps` + `docker rm -f`).
- **Decyzja**: SKIPPED
