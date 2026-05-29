# Porządki w strukturze projektu — Krótki plan

> Pełny plan: `context/changes/project-structure-cleanup/plan.md`

## Co i dlaczego

Projekt nagromadził artefakty podczas intensywnego sprintu S-04: 5 folderów zmian nigdy nie zostało zarchiwizowanych (status `implemented`/`impl_reviewed`), katalog `.tmp/UI/` z 18 plikami pre-reskin prototypu pozostał po zastąpieniu go finalnym designem, oraz dwa scaffoldowe pliki KMP nigdy nie zostały usunięte. Celem jest doprowadzenie `context/changes/`, katalogu głównego i `commonMain` do stanu bez zbędnych artefaktów.

## Punkt wyjścia

`context/changes/` zawiera 5 zaległych folderów ze statusem `implemented`/`impl_reviewed`, katalog `.tmp/` z prototypem UI, oraz `bootstrap-verification/`. W `commonMain` istnieją `Greeting.kt` i `GreetingUtil.kt` bez żadnych referencji.

## Pożądany stan końcowy

`context/changes/` zawiera tylko aktywne zmiany. Katalog `.tmp/` nie istnieje. `commonMain` nie ma scaffoldowych plików. Kompilacja KMP przechodzi.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego |
|---------|-------|----------|
| Stare change foldery | Archiwizuj przez git mv | Zachowuje historię, spójny z workflow |
| .tmp/UI/ | Usuń git rm -r | Zawsze tymczasowy, w całości zastąpiony przez S-04 |
| Greeting.kt/GreetingUtil.kt | Usuń | Zero referencji, scaffoldowy artefakt |
| jsMain/ source set | Zostaw | Użytkownik zdecydował nie ruszać |
| bootstrap-verification/ | Usuń | Nie jest standardowym change, artefakt CLI |

## Zakres

**W zakresie:**
- Archiwizacja 5 folderów: `s-01`, `s-02a`, `ui-reskin-design-system`, `ui-reskin-screens`, `ui-reskin-new-screens`
- Usunięcie `.tmp/` (18 plików)
- Usunięcie `Greeting.kt`, `GreetingUtil.kt`, `bootstrap-verification/`

**Poza zakresem:**
- `jsMain/` source set
- `iosMain/` / `iosApp/`
- Build artifacts (`.gradle/`, `build/`, `target/`)
- Modyfikacje CLAUDE.md, AGENTS.md, roadmapy poza wpisami ## Done

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|--------------|-----------------|
| 1. Archive stale changes | 5 folderów przeniesione do context/archive/ | S-01/s-02a nie mają pasującego Change ID w roadmapie — brak edycji roadmapy (to OK) |
| 2. Usuń .tmp/UI/ | Katalog główny bez .tmp/ | Brak — git rm zachowuje historię |
| 3. Usuń scaffold | Build przechodzi bez Greeting* | Brak — pliki bez referencji |

**Wymagania wstępne:** Czyste working tree (wszystkie zmiany zatwierdzone)
**Szacowany nakład:** ~1 sesja, 3 atomowe commity

## Otwarte ryzyka i założenia

- `s-01` i `s-02a` nie mają Change ID pasującego do roadmapy — archiwizacja nie zaktualizuje roadmapy (S-01 jest już `done`)
- S-04-A/B/C są już `done` w roadmapie — `/10x-archive` doda je tylko do `## Done` section

## Kryteria sukcesu

- `context/changes/` zawiera tylko `project-structure-cleanup/`
- Kompilacja `./gradlew :shared:compileDebugKotlinAndroid` przechodzi
- Repozytorium bez artefaktów tymczasowych
