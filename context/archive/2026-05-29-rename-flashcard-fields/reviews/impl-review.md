<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Rename flashcard fields

- **Plan**: brak plan.md — przegląd commitu d5577ce
- **Scope**: pełny commit (rename + Cargo.toml + docs)
- **Date**: 2026-05-29
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension            | Verdict |
|----------------------|---------|
| Plan Adherence       | PASS    |
| Scope Discipline     | WARNING |
| Safety & Quality     | WARNING |
| Architecture         | PASS    |
| Pattern Consistency  | PASS    |
| Success Criteria     | PASS    |

## Findings

### F1 — panic = "abort" usuwa graceful recovery na paniku

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; warto przemyśleć
- **Dimension**: Safety & Quality
- **Location**: apps/backend/Cargo.toml:22–26
- **Detail**: Niezłapany panic! / unwrap() na gorącej ścieżce ubiłby cały proces Render.com zamiast zwrócić HTTP 500. strip = true utrudniałby debugging. LTO + codegen-units=1 mogły powodować timeout buildu.
- **Fix A ⭐**: Usuń panic = "abort" i strip = true; zachowaj lto, opt-level z, codegen-units=1.
  - Strength: Zachowuje optymalizację rozmiaru binarki bez ryzyka cichego ubicia procesu.
  - Tradeoff: Binarka nieco większa (symbole debugowania).
  - Confidence: HIGH
  - Blind spot: Nie sprawdzono ile unwrap() jest na gorącej ścieżce.
- **Decision**: FIXED via Fix A (usunięto panic="abort" i strip=true z Cargo.toml)

### F2 — Brak atomiczności deploy: backend+frontend muszą wyjść razem

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; warto przemyśleć
- **Dimension**: Safety & Quality
- **Location**: kontrakt API (FlashcardRequest / FlashcardUpdateRequest)
- **Detail**: Zmiana nazw pól JSON to breaking change. APK zainstalowany przed aktualizacją backendu wyśle stare nazwy pól i dostanie błąd lub cichy null. Mitigacja: jedyny konsument (Android) zaktualizowany w tym samym commicie.
- **Fix**: Dodaj notę deployment order do AGENTS.md.
- **Decision**: SKIPPED

### F3 — [profile.release] poza zakresem renamingu

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja
- **Dimension**: Scope Discipline
- **Location**: apps/backend/Cargo.toml:21–26
- **Detail**: Sekcja [profile.release] nie była częścią rename scope. Commit message ją opisuje, zmiana świadoma. Częściowo naprawiona przez F1.
- **Decision**: SKIPPED

### F4 — Migracja 007 nie jest idempotentna (brak IF EXISTS)

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja
- **Dimension**: Safety & Quality
- **Location**: apps/backend/migrations/007_rename_flashcard_columns.sql
- **Detail**: RENAME COLUMN bez IF EXISTS wyrzuci błąd przy ponownym uruchomieniu. SQLx checksumuje migracje i nie uruchamia ponownie tej samej — ryzyko zerowe.
- **Decision**: SKIPPED
