# Porządki w strukturze projektu — Plan implementacji

## Przegląd

Sprzątnięcie artefaktów narosłych podczas sprintu S-04: 5 folderów zmian ze statusem `implemented`/`impl_reviewed` które nigdy nie zostały zarchiwizowane, obsoletny katalog `.tmp/UI/` z prototypem UI pre-reskin oraz scaffoldowe pliki z szablonu KMP.

## Analiza stanu obecnego

**context/changes/ — foldery do zarchiwizowania:**

| Folder | Status | created | Odpowiednik w roadmapie |
|--------|--------|---------|------------------------|
| `s-01` | `implemented` | 2026-05-27 | S-01 (`collections-flashcards-e2e`) — done |
| `s-02a` | `impl_reviewed` | 2026-05-27 | brak wpisu (401 interceptor) |
| `ui-reskin-design-system` | `implemented` | 2026-05-28 | S-04-A (`ui-reskin-design-system`) — done |
| `ui-reskin-screens` | `implemented` | 2026-05-28 | S-04-B (`ui-reskin-screens`) — done |
| `ui-reskin-new-screens` | `implemented` | 2026-05-29 | S-04-C (`ui-reskin-new-screens`) — done |

**Uwaga:** S-04-A/B/C mają już status `done` w roadmapie — `/10x-archive` zaktualizuje `## Done` section i zachowa historię przez `git mv`.

**S-01 i s-02a** — roadmapa nie ma Change ID równego `s-01` ani `s-02a` (roadmapa używa `collections-flashcards-e2e`), więc `/10x-archive` wykona git mv + zmianę statusu change.md, ale nie znajdzie pasującego wpisu roadmapy. To zachowanie prawidłowe.

**.tmp/UI/ — 18 plików:**
- Oryginalny prototyp designu (pre-reskin), użyty jako wzorzec podczas S-04
- Zastąpiony przez `frontend/shared/src/commonMain/.../theme/`, `ui/components/`, wszystkie ekrany
- Nazwa `.tmp/` zawsze sygnalizowała tymczasowy charakter

**Scaffold placeholders:**
- `Greeting.kt` — plik z szablonu KMP, funkcja `greet()` niezaimportowana nigdzie
- `GreetingUtil.kt` — to samo, zero referencji poza własnym plikiem
- `bootstrap-verification/` — artefakt CLI bootstrappera (verification.md bez standardowego frontmatter change.md)

## Pożądany stan końcowy

- `context/changes/` zawiera tylko: `project-structure-cleanup/` (aktywne) — żadnych zaległych implemented/impl_reviewed
- `.tmp/` nie istnieje w katalogu głównym
- `commonMain` nie zawiera `Greeting.kt` ani `GreetingUtil.kt`
- `context/changes/bootstrap-verification/` nie istnieje
- Build frontendu przechodzi: `./gradlew :shared:compileDebugKotlinAndroid`

## Czego NIE robimy

- Nie ruszamy `jsMain/` source setu (zostawiamy jak jest)
- Nie modyfikujemy `iosMain/` ani `iosApp/` (poza zakresem MVP)
- Nie czyścimy build artifacts (`.gradle/`, `build/`, `target/`) — to należy do `./gradlew clean` / `cargo clean`
- Nie przebudowujemy struktury katalogów frontendu ani backendu
- Nie modyfikujemy `CLAUDE.md` / `AGENTS.md`

## Podejście do implementacji

Trzy atomowe commity odpowiadające fazom. Każda faza weryfikuje się przez brak błędów kompilacji (faza 3) lub poprawność git statusu (fazy 1-2).

---

## Faza 1: Archive stale change folders

### Przegląd

Przeniesienie 5 zaległych folderów zmian do `context/archive/` przez `git mv` z aktualizacją `change.md` (status: archived, archived_at). Zachowamy historię git i (gdzie pasuje Change ID) zaktualizujemy roadmapę.

### Wymagane zmiany:

#### 1. s-01

**Plik**: `context/changes/s-01/change.md`

**Cel**: Zaktualizować status na `archived`, ustawić `archived_at`, przenieść folder.

**Kontrakt**: `git mv context/changes/s-01 context/archive/2026-05-27-s-01`; change.md: `status: archived`, `archived_at: <ISO-UTC>`, `updated: 2026-05-29`. Roadmapa nie zostanie dotknięta (brak Change ID `s-01` w roadmapie).

#### 2. s-02a

**Plik**: `context/changes/s-02a/change.md`

**Cel**: Zaktualizować status na `archived`, przenieść folder.

**Kontrakt**: `git mv context/changes/s-02a context/archive/2026-05-27-s-02a`; change.md: `status: archived`, `archived_at: <ISO-UTC>`, `updated: 2026-05-29`. Brak wpisu w roadmapie — brak edycji roadmapy.

#### 3. ui-reskin-design-system

**Plik**: `context/changes/ui-reskin-design-system/change.md`

**Cel**: Zaktualizować status na `archived`, przenieść folder. Change ID pasuje do S-04-A w roadmapie — `/10x-archive` doda wpis do `## Done`.

**Kontrakt**: `git mv context/changes/ui-reskin-design-system context/archive/2026-05-28-ui-reskin-design-system`; change.md: `status: archived`, `archived_at: <ISO-UTC>`, `updated: 2026-05-29`. Roadmapa: S-04-A już `done` — dodać wpis `## Done`.

#### 4. ui-reskin-screens

**Plik**: `context/changes/ui-reskin-screens/change.md`

**Cel**: Jak wyżej dla S-04-B.

**Kontrakt**: `git mv context/changes/ui-reskin-screens context/archive/2026-05-28-ui-reskin-screens`; change.md: `status: archived`, `archived_at: <ISO-UTC>`, `updated: 2026-05-29`. Roadmapa: S-04-B → wpis `## Done`.

#### 5. ui-reskin-new-screens

**Plik**: `context/changes/ui-reskin-new-screens/change.md`

**Cel**: Jak wyżej dla S-04-C.

**Kontrakt**: `git mv context/changes/ui-reskin-new-screens context/archive/2026-05-29-ui-reskin-new-screens`; change.md: `status: archived`, `archived_at: <ISO-UTC>`, `updated: 2026-05-29`. Roadmapa: S-04-C → wpis `## Done`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- `context/changes/` nie zawiera folderów: `s-01`, `s-02a`, `ui-reskin-design-system`, `ui-reskin-screens`, `ui-reskin-new-screens`
- `context/archive/` zawiera: `2026-05-27-s-01`, `2026-05-27-s-02a`, `2026-05-28-ui-reskin-design-system`, `2026-05-28-ui-reskin-screens`, `2026-05-29-ui-reskin-new-screens`
- `git status` czysty po commicie

#### Weryfikacja ręczna:

- `roadmap.md` `## Done` ma nowe wpisy dla S-04-A, S-04-B, S-04-C

---

## Faza 2: Usuń obsoletny prototyp .tmp/UI/

### Przegląd

Usunięcie katalogu `.tmp/` z 18 plikami pre-reskin prototypu. Folder był zawsze tymczasowy (sygnalizuje to nazwa), a jego zawartość jest w całości zastąpiona przez finalne ekrany i komponenty z S-04.

### Wymagane zmiany:

#### 1. Usunięcie .tmp/

**Plik**: `.tmp/` (cały katalog)

**Cel**: Usunąć katalog i wszystkie 18 plików.

**Kontrakt**: `git rm -r .tmp/`

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- `.tmp/` nie istnieje w katalogu głównym projektu
- `git status` czysty po commicie

#### Weryfikacja ręczna:

- (brak — usunięcie plików nie wymaga testowania ręcznego)

---

## Faza 3: Usuń scaffold placeholders i bootstrap-verification

### Przegląd

Usunięcie trzech pozostałych artefaktów: dwóch scaffoldowych plików KMP (`Greeting.kt`, `GreetingUtil.kt`) bez referencji w projekcie, oraz folderu `bootstrap-verification/` który nie jest standardowym change.

### Wymagane zmiany:

#### 1. Greeting.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/Greeting.kt`

**Cel**: Usunąć plik — brak referencji w projekcie, scaffoldowy artefakt z KMP template.

**Kontrakt**: `git rm Greeting.kt`

#### 2. GreetingUtil.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/GreetingUtil.kt`

**Cel**: Usunąć plik — brak referencji w projekcie.

**Kontrakt**: `git rm GreetingUtil.kt`

#### 3. bootstrap-verification/

**Plik**: `context/changes/bootstrap-verification/`

**Cel**: Usunąć folder — artefakt bootstrappera CLI, nie standardowy change.

**Kontrakt**: `git rm -r context/changes/bootstrap-verification/`

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja KMP przechodzi: `./gradlew :shared:compileDebugKotlinAndroid`
- `Greeting.kt` i `GreetingUtil.kt` nie istnieją w `commonMain`
- `context/changes/bootstrap-verification/` nie istnieje

#### Weryfikacja ręczna:

- (brak — usunięcie scaffoldu nie zmienia funkcjonalności)

---

## Referencje

- Powiązana zmiana: `context/changes/project-structure-cleanup/change.md`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Archive stale change folders

#### Automatyczne

- [x] 1.1 context/changes/ nie zawiera 5 zarchiwizowanych folderów — bb1869f
- [x] 1.2 context/archive/ zawiera 5 nowych folderów (daty zgodne z created) — bb1869f
- [x] 1.3 git status czysty po commicie fazy 1 — bb1869f

#### Ręczne

- [x] 1.4 roadmap.md ## Done ma nowe wpisy dla S-04-A, S-04-B, S-04-C — bb1869f

### Faza 2: Usuń .tmp/UI/

#### Automatyczne

- [x] 2.1 .tmp/ nie istnieje w katalogu głównym
- [x] 2.2 git status czysty po commicie fazy 2

### Faza 3: Usuń scaffold i bootstrap-verification

#### Automatyczne

- [x] 3.1 ./gradlew :shared:compileDebugKotlinAndroid przechodzi — 6661f2a
- [x] 3.2 Greeting.kt i GreetingUtil.kt nie istnieją w commonMain — 6661f2a
- [x] 3.3 context/changes/bootstrap-verification/ nie istnieje — 6661f2a
