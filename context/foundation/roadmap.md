---
project: "FiszkiWBiegu"
version: 1
status: draft
created: 2026-05-27
updated: 2026-05-29
prd_version: 1
main_goal: market-feedback
top_blocker: decisions
---

# Mapa drogowa: FiszkiWBiegu

> Pochodzi z `context/foundation/prd.md` (v1) + automatycznie zbadana baza kodu (2026-05-27).
> Edytuj na miejscu; archiwizuj po zastąpieniu.
> Fragmenty poniżej są wymienione w kolejności zależności. Tabela „W skrócie" to indeks.

## Podsumowanie wizji

Biegacze tracą dziesiątki godzin miesięcznie, które mogłyby być poświęcone na naukę słownictwa — bo istniejące aplikacje fiszek wymagają wzroku i rąk. FiszkiWBiegu odwzorowuje cykl nauki fiszka → pauza → odpowiedź wyłącznie w audio: użytkownik słyszy słowo po polsku, odpowiada w myślach, a po chwili słyszy 3-krotne tłumaczenie po angielsku. Aplikacja działa w tle, gdy ekran jest wyłączony, a przyciskami słuchawek Bluetooth (PLAY/PAUSE/NEXT/PREV) użytkownik steruje sesją bez dotykania telefonu.

## Gwiazda przewodnia

**S-02: Kompletna sesja nauki audio offline** — użytkownik przeprowadza pierwszą sesję nauki podczas rzeczywistego biegu: TTS + Foreground Service + sterowanie słuchawkami, bez dostępu do internetu.

> Gwiazda przewodnia — najmniejszy, kompleksowy przepływ, który udowadnia podstawową hipotezę produktu — to S-02, ponieważ dopiero gdy Rafał przebiegnie 45 minut z telefonem w kieszeni i usłyszy wszystkie fiszki bez przerw, hipoteza jest zwalidowana. Wszystko inne (CRUD, wdrożenie) ma wartość tylko jako infrastruktura pod to zdarzenie.

## W skrócie

| ID   | ID zmiany                      | Wynik (użytkownik może …)                                                          | Wymagania wstępne | Odniesienia do PRD                                | Status   |
| ---- | ------------------------------ | ---------------------------------------------------------------------------------- | ----------------- | ------------------------------------------------- | -------- |
| F-01 | offline-flashcard-cache        | (fundament) fiszki zsynchronizowane lokalnie; tryb nauki nie wymaga internetu      | —                 | NFR (offline), Open Question #2                   | done     |
| S-01 | collections-flashcards-e2e    | zalogować się, tworzyć/przeglądać/edytować/usuwać kolekcje i fiszki               | —                 | US-02, FR-001–FR-009                              | done     |
| S-02 | audio-learning-session-offline | uruchomić tryb nauki i słyszeć fiszki offline podczas biegu z ekranem wyłączonym  | F-01, S-01        | US-01, FR-010–FR-014, NFR (offline, audio, latency) | done     |
| S-03 | production-run-validation      | zainstalować APK i przeprowadzić pełną sesję nauki podczas rzeczywistego biegu    | S-02              | US-01, FR-012                                     | done     |
| S-04-A | ui-reskin-design-system    | (faza A) design system i komponenty wgrane, projekt kompiluje się z nową paletą   | S-03              | —                                                 | done     |
| S-04-B | ui-reskin-screens          | (faza B) 4 istniejące ekrany reskinowane; aplikacja wygląda jak projekt graficzny | S-04-A            | —                                                 | done     |
| S-04-C | ui-reskin-new-screens      | (faza C) 3 nowe ekrany (stub dane) + nawigacja bottom-tab                         | S-04-B            | —                                                 | done     |
| S-04-D | ui-reskin-backend-stubs    | (faza D) realne dane zamiast zaślepek: /me, lastStudied, progress, translate      | S-04-C            | —                                                 | done     |
| S-05   | ui-tweaks                  | lista kolekcji bez menu + subtitle "N fiszek · X dni temu"; edycja/usuń kolekcji w widoku szczegółów; formularze nie zasłaniane przez klawiaturę | S-04-D | — | done |
| S-06   | rename-flashcard-fields    | przemianowanie pól fiszki: `polish_text`→`source_text`, `english_text`→`target_text` w DB, API i całym frontendzie | S-05 | — | done |
| S-07   | frontend-improvements      | zmiany i optymalizacje w UI lub kodzie frontendu; realizowane etapami, zamknięte przez impl-review | S-06 | — | done |

## Strumienie

Pomoc nawigacyjna — grupuje elementy, które dzielą łańcuch wymagań wstępnych. Kanoniczna kolejność nadal znajduje się w grafie zależności poniżej; ta tabela to proponowana kolejność czytania równoległych ścieżek.

| Strumień | Temat                   | Łańcuch                           | Uwaga                                                                     |
| -------- | ----------------------- | --------------------------------- | ------------------------------------------------------------------------- |
| A        | Offline i sesja nauki   | `F-01` → `S-02` → `S-03`         | Ścieżka do gwiazdy przewodniej; F-01 jest kluczowym odblokowanym elementem. |
| B        | CRUD i uwierzytelnianie | `S-01` → (dołącza do A w `S-02`) | Równolegle z F-01; razem z F-01 odblokowuje S-02.                         |

## Baza

Co już jest na miejscu w bazie kodu na dzień 2026-05-29 (automatycznie zbadane + potwierdzone przez użytkownika).
Fundamenty poniżej zakładają, że te elementy są obecne i NIE tworzą ich ponownie.

- **Frontend:** obecny — Compose Multiplatform + Material3 + design system Dawn Run (paleta, czcionki Bricolage/JetBrains Mono); routing navigation3-compose (App.kt, mutableStateListOf backstack); 7 ekranów: LoginScreen, CollectionsScreen, FlashcardsScreen, LearningScreen, ProfileScreen, CollectionFormScreen, CardFormScreen; bottom-tab bar (Kolekcje / Konto); moduły androidApp i webApp
- **Backend / API:** obecny — Actix-web 4.13; auth JWT (Google OAuth); endpointy: POST /auth/login; GET|POST /collections, PUT|DELETE /collections/{id}; GET|POST /collections/{id}/flashcards, GET /collections/{id}/learning; PUT|DELETE /flashcards/{id}; walidacja: kody języka (pl/en/de/es/fr/it), source ≠ target, nazwa niepusta (422)
- **Dane:** częściowy — sqlx + PostgreSQL (backend); migracje: `001_init` (collections + flashcards, indeksy), `002_add_users` (tabela users + FK collections→users), `003_add_languages` (source/target_language w collections), `004_add_description` (description w collections), `005_add_user_profile` (display_name, streak_days w users), `006_add_collection_tracking` (last_studied, progress, flashcard_count w collections), `007_rename_flashcard_columns` (polish_text→source_text, english_text→target_text; S-06); schemat: `users(google_id, email, display_name, streak_days)`, `collections(user_id, name, description, source_language, target_language, last_studied, progress, flashcard_count)`, `flashcards(collection_id, source_text, target_text, position)`; BRAK lokalnego cache fiszek we frontendzie (dane sieciowe); token JWT persistowany przez multiplatform-settings
- **Autoryzacja:** obecna — backend: Google id_token validation (`auth.rs`), JWT issue/verify, `AuthUser` extractor na trasach; frontend: `GoogleSignInHelper.kt` (Android Credential Manager), `AuthRepository.kt` (login/logout/isLoggedIn + token storage), `AuthEventBus.kt` (unauthorizedEvents → auto-logout), `LoginScreen.kt`
- **Wdrożenie / infra:** częściowe — GitHub Actions CI: backend (`cargo build --release` + `cargo test` na push/PR do master); BRAK pipeline CI dla frontendu (APK budowany lokalnie); backend: auto-deploy via Render.com (`render.yaml`, Frankfurt, starter plan, `healthCheckPath: /health`); env vars w panelu Render: DATABASE_URL, JWT_SECRET, GOOGLE_CLIENT_ID; BRAK Dockerfile (Render używa natywnego runtime Rust)
- **Obserwowalność:** minimalna — backend: wyłącznie `eprintln!` na błędach DB/JWT (13 miejsc w handlerach); brak `tracing`/`log`; logi dostępne w panelu Render.com; frontend: brak Crashlytics / Sentry; brak metryk, alertów ani dashboardu

## Fundamenty

### F-01: Cache i synchronizacja fiszek offline

- **Wynik:** (fundament) fiszki pobrane z backendu i przechowywane lokalnie na urządzeniu Android; tryb nauki nie wymaga aktywnego połączenia z internetem po uruchomieniu sesji.
- **ID zmiany:** offline-flashcard-cache
- **Odniesienia do PRD:** NFR („odtwarzanie audio i dostęp do treści fiszek kolekcji nie są uzależnione od łączności sieciowej po uruchomieniu sesji"), Open Question #2
- **Odblokowuje:** S-02 — compliance z NFR offline; bez lokalnego cache S-02 łamie NFR przy pierwszej utracie zasięgu podczas biegu
- **Wymagania wstępne:** — cała kolekcja, która ma być wykorzystywana w trybie nauki, ma być pobrana i dostępna w lokalnym cache
- **Równolegle z:** S-01
- **Blokady:** —
- **Niewiadome:** -
- **Ryzyko:** -
- **Status:** done

## Fragmenty

### S-01: Zarządzanie kolekcjami i fiszkami E2E

- **Wynik:** użytkownik może zalogować się przez Google, tworzyć/przeglądać/edytować/usuwać kolekcje i fiszki, przy każdym usunięciu pojawia się dialog potwierdzenia.
- **ID zmiany:** collections-flashcards-e2e
- **Odniesienia do PRD:** US-02, FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009
- **Wymagania wstępne:** —
- **Równolegle z:** F-01
- **Blokady:** —
- **Niewiadome:** —
- **Ryzyko:** ~~Kod warstw auth + CRUD + UI jest obecny, ale E2E integracja mogła nie być testowana kompleksowo~~ — zweryfikowane E2E; S-03 potwierdził stabilność na urządzeniu produkcyjnym. Formularze kolekcji i fiszek przeniesione do dedykowanych ekranów (CollectionFormScreen, CardFormScreen) w ramach S-04-C. Kolekcje rozszerzone o `description`, `source_language`, `target_language` (collection-language-select).
- **Status:** done

### S-02: Kompletna sesja nauki audio offline

- **Wynik:** użytkownik może wybrać kolekcję, uruchomić tryb nauki i słyszeć fiszki przez TTS podczas biegu z ekranem wyłączonym, sterując przyciskami słuchawek (PLAY/PAUSE/NEXT/PREV), bez dostępu do internetu; po ostatniej fiszce pętla wraca do pierwszej; aplikacja zapamiętuje ostatnio używaną kolekcję.
- **ID zmiany:** audio-learning-session-offline
- **Odniesienia do PRD:** US-01, FR-010, FR-011, FR-012, FR-013, FR-014, NFR (ciągłość audio <200ms, offline, latencja słuchawek <500ms)
- **Wymagania wstępne:** S-01, F-01
- **Równolegle z:** —
- **Blokady:** —
- **Niewiadome:**
  - Długość pauz w cyklu audio: ile sekund po tekście PL (przed EN) i między powtórzeniami EN? — Właściciel: Rafał. Blokada: nie (do ustalenia empirycznie podczas implementacji; implementacja startuje od wartości roboczej i refinuje w trakcie testów na urządzeniu).
- **Ryzyko:** LearningService + TTS + MediaSession są obecne i aktywnie debugowane (bugfix drugiego wejścia w tryb nauki, przerwy na podstawie trwania wypowiedzi), ale integracja z offline cache może ujawnić problemy z kolejnością inicjalizacji; awarie w tle są nienaprawialne gdy telefon jest w kieszeni.
- **Status:** done

### S-03: Weryfikacja produkcyjna — pierwsze bieganie

- **Wynik:** użytkownik instaluje APK, aplikacja łączy się z backendem na Render.com, Rafał przeprowadza pierwszą pełną sesję nauki podczas rzeczywistego biegu (30-60 min, ekran wyłączony, słuchawki Bluetooth).
- **ID zmiany:** production-run-validation
- **Odniesienia do PRD:** US-01, FR-012, NFR (ciągłość audio przez 30-60 min, offline, brak crashy w tle)
- **Wymagania wstępne:** S-02
- **Równolegle z:** —
- **Blokady:** Render.com env vars — SUPABASE_SERVICE_ROLE_KEY i DATABASE_URL muszą być skonfigurowane w panelu Render przed pierwszym uruchomieniem produkcyjnym.
- **Niewiadome:** —
- **Ryzyko:** Problemy ze stabilnością Foreground Service (memory leaks, wakelocks, Android doze mode) ujawnią się dopiero podczas 45-60 minutowego biegu na prawdziwym urządzeniu; im wcześniej przetestowane, tym mniej niespodzianek przy docelowym użyciu.
- **Status:** done

### S-04: Reskin UI — przeniesienie projektu graficznego

- **Wynik:** aplikacja wygląda jak projekt graficzny z `.tmp/UI` (paleta „Dawn Run", czcionki Bricolage + JetBrains Mono, layout „tor biegowy"); funkcjonalność bez zmian; brakujące dane zaślepione.
- **ID zmiany:** ui-reskin
- **Odniesienia do PRD:** —
- **Wymagania wstępne:** S-03
- **Równolegle z:** —
- **Blokady:** —
- **Niewiadome:** —
- **Ryzyko:** Prototyp używa pakietu `pl.fiszki.wbiegu`; istniejący kod używa `pl.rkarpinski.fiszkiwbiegu` — pełna adaptacja package names przy kopiowaniu plików.
- **Status:** done

#### S-04-A: Design system + komponenty bazowe

**Wynik:** moduł shared kompiluje się z nową paletą i typografią; stare ekrany nadal działają (bez reskinowania).

Zakres:
1. Skopiuj `theme/Color.kt`, `theme/Theme.kt`, `theme/Type.kt` → `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/`
2. Pobierz fonty (Bricolage Grotesque: Regular/SemiBold/Bold; JetBrains Mono: Regular/Bold) z Google Fonts → `apps/frontend/shared/src/commonMain/composeResources/font/`
3. Skopiuj `ui/components/Flag.kt`, `Components.kt`, `MediaControls.kt` → `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/`
4. Zaktualizuj `apps/frontend/shared/build.gradle.kts`: dodaj `compose.components.resources`, `material-icons-extended`
5. Weryfikacja: `./gradlew :shared:compileDebugKotlinAndroid` bez błędów

#### S-04-B: Reskin istniejących ekranów

**Wynik:** 4 istniejące przepływy aplikacji wyglądają jak projekt graficzny; logika ViewModeli bez zmian.

Zakres:
1. `LoginScreen.kt` → przepisać layout do `AuthScreen.kt` (brand hero + 3 przyciski social); Apple + Facebook = `Button(enabled=false)` + Toast "Wkrótce"
2. `CollectionsScreen.kt` → przepisać do Variant B (header z pozdrowieniem + LastUsedHero + lane rows); pola stub: `lastStudied=null` (hero hidden), `progress=0f` (TrackBar pusta), accent = `accentColor(collection.id)` deterministycznie z id
3. `FlashcardsScreen.kt` → przepisać do `CollectionDetailScreen` (top bar + hero + stats stub + CTA „Słuchaj w biegu" + LazyColumn fiszek)
4. `LearningScreen.kt` → przepisać do `StudyScreen` Variant B (card stage z fazami PL/EN, MediaControls)
5. `App.kt` → dolny tab bar: zakładki Kolekcje + Konto (ProfileScreen = stub na ten etap)
6. Weryfikacja: APK instaluje się, wszystkie 4 ekrany wyświetlają nowy design, sesja nauki działa

#### S-04-C: Nowe ekrany + kompletna nawigacja

**Wynik:** pełne 7-ekranowe drzewo nawigacji; nowe ekrany mają stub dane tam gdzie brak API.

Zakres:
1. `ProfileScreen.kt` — nowy ekran: avatar (gradient Ember→Peach, inicjały), email z `TokenStorage`, displayName = stub "Ty", streakDays = 0 (chip hidden), przycisk Wyloguj
2. `CollectionFormScreen.kt` — zastępuje dialogi `AddCollectionDialog` / `EditCollectionDialog`; pola: nazwa + opis; bottom-sheet ConfirmDelete (istniejąca logika VM)
3. `CardFormScreen.kt` — zastępuje `FlashcardFormDialog`; pola: PL + EN z flagami; przycisk Przetłumacz = `Button(enabled=false)` + Toast "Wkrótce"
4. `App.kt` → zaktualizuj nawigację: Login → Collections → CollectionDetail → StudyScreen; Collections ↔ Profile (tab bar); Collections/Detail → CollectionForm; Detail → CardForm
5. Weryfikacja: pełna nawigacja działa E2E; wszystkie destrukcyjne akcje mają potwierdzenie (PRD hard rule)

#### S-04-D: Backend — dane pod zaślepki (backlog)

**Wynik:** zaślepki zastąpione realnymi danymi.

Zakres (każdy punkt = osobny `/10x-plan`):
- GET `/auth/me` → displayName, streakDays (Rust backend + migracja DB)
- Backend track `lastStudied` per kolekcja → pole + endpoint
- Backend track `progress` per kolekcja (count learned / total) → pole + endpoint
- `Collection.icon` → pole emoji/string w DB + API
- `TranslateService` Android actual → ML Kit on-device translate (PL⇄EN)
- Apple/Facebook Sign-In → pełna implementacja (poza MVP scope — v2)

**Zaślepki aktywne po S-04-C:**

| Zaślepka | Zachowanie | Faza docelowa |
| -------- | ---------- | ------------- |
| `Collection.lastStudied` | null → LastUsedHero ukryty | S-04-D |
| `Collection.progress` | 0.0f → TrackBar pusta | S-04-D |
| `Collection.icon/accent` | deterministyczny kolor z id (6-kolorowa paleta) — brak pola w DB | S-04-D |
| `UserState.streakDays` | 0 → streak chip ukryty | S-04-D |
| `ProfileScreen` displayName | "Ty" (stub) — brak endpointu `/auth/me` | S-04-D |
| `TranslateService` | button disabled + Toast "Wkrótce" | S-04-D |
| Apple Sign-In | button disabled + Toast "Wkrótce" | poza MVP |
| Facebook Sign-In | button disabled + Toast "Wkrótce" | poza MVP |

### S-05: Poprawki UI

- **Wynik:** lista kolekcji bez menu MoreVert przy każdej pozycji; subtitle „N fiszek · X dni temu" widoczny pod nazwą kolekcji; edycja i usuwanie kolekcji dostępne z widoku szczegółów (MoreVert w top barze FlashcardsScreen); fiszki mają MoreVert zamiast TextButton; formularze kolekcji i fiszek nie są zasłaniane przez klawiaturę.
- **ID zmiany:** ui-tweaks
- **Odniesienia do PRD:** —
- **Wymagania wstępne:** S-04-D
- **Równolegle z:** —
- **Blokady:** —
- **Niewiadome:** —
- **Ryzyko:** —
- **Status:** done

### S-07: Zmiany w kodzie frontend

- **Wynik:** zmiany i optymalizacje w UI lub kodzie frontendu; każda zmiana realizowana etapem przez prompt i weryfikowana impl-review na końcu.
- **ID zmiany:** frontend-improvements
- **Odniesienia do PRD:** —
- **Wymagania wstępne:** S-06
- **Równolegle z:** —
- **Blokady:** —
- **Niewiadome:** —
- **Ryzyko:** —
- **Status:** done

### S-06: Przemianowanie pól fiszki

- **Wynik:** kolumny DB `polish_text`/`english_text` przemianowane na `source_text`/`target_text`; odpowiednie zmiany w modelach Rust, zapytaniach SQL, serializacji JSON (backend i frontend) oraz wszystkich warstwach frontendu (Repository, ViewModel, ekrany, LearningService).
- **ID zmiany:** rename-flashcard-fields
- **Odniesienia do PRD:** —
- **Wymagania wstępne:** S-05
- **Równolegle z:** —
- **Blokady:** wymaga migracji DB `007_rename_flashcard_columns.sql` na środowisku produkcyjnym
- **Niewiadome:** —
- **Ryzyko:** —
- **Status:** done

## Przekazanie do backlogu

| ID mapy drogowej | ID zmiany                           | Sugerowany tytuł problemu                                     | Gotowe do `/10x-plan` | Uwagi                                         |
| ---------------- |-------------------------------------| ------------------------------------------------------------- |-----------------------|-----------------------------------------------|
| F-01             | offline-flashcard-cache             | Cache i synchronizacja fiszek offline (Android Room/SQLite)  | done                  | Uruchom `/10x-plan offline-flashcard-cache`   |
| S-01             | collections-flashcards-e2e          | Zarządzanie kolekcjami i fiszkami — weryfikacja E2E           | done                  | Zrealizowane                                  |
| S-02             | audio-learning-session-offline      | Tryb nauki audio offline — integracja i testy                 | done                  | Zrealizowane                                  |
| S-03             | production-run-validation           | Wdrożenie produkcyjne + pierwsza sesja na żywo                | done                  | Zrealizowane                                  |
| S-04-A           | ui-reskin-design-system             | Reskin UI — faza A: design system                             | done                  | Zrealizowane                                  |
| S-04-B           | ui-reskin-screens                   | Reskin UI — faza B: reskin istniejących ekranów               | done                  | Zrealizowane                                  |
| S-04-C           | ui-reskin-new-screens               | Reskin UI — faza C: nowe ekrany + nawigacja                   | done                  | Zrealizowane                                  |
| S-04-D           | ui-reskin-backend-stubs             | Reskin UI — faza D: backend dla zaślepek                      | done                  | Zrealizowane                                  |
| S-05             | ui-tweaks                           | Poprawki UI — menu, subtitle, klawiatura                      | done                  | Zrealizowane                                  |
| S-06             | rename-flashcard-fields             | Przemianowanie pól fiszki: polish_text→source_text, english_text→target_text | done          | Zrealizowane                                  |
| S-07             | frontend-improvements               | Zmiany i optymalizacje w UI lub kodzie frontendu                              | done          | Zrealizowane                                  |

## Otwarte pytania dotyczące mapy drogowej

_(brak aktywnych pytań — wszystkie zablokowane przez F-01/S-02 zostały rozwiązane w trakcie implementacji)_

## Zaparkowane

- **FR-015: Tryb EN→PL (3x angielski, potem 1x polski)** — Dlaczego zaparkowane: PRD §Sokrates — „PL→EN to główny use case; EN→PL komplikuje UI bez kluczowej wartości dla MVP"; planowane w v2.
- **Import fiszek z plików (PDF, CSV, Anki, DOCX)** — Dlaczego zaparkowane: PRD §Non-Goals — tylko ręczne tworzenie w MVP; import w v2.
- **Tryb nauki na Web** — Dlaczego zaparkowane: PRD §Non-Goals — webApp służy wyłącznie do zarządzania fiszkami; audio learning działa tylko na Android.
- **Obsługa iPhone (iOS)** (GitHub #2) — Dlaczego zaparkowane: PRD §Non-Goals — cel iOS istnieje w szablonie KMP, nie aktywowany w MVP; v2.
- **Spaced repetition / ocenianie (UMIEM/NIE UMIEM)** — Dlaczego zaparkowane: PRD §Non-Goals — brak algorytmu powtórek w MVP.
- **Współdzielenie fiszek między użytkownikami** — Dlaczego zaparkowane: PRD §Non-Goals — fiszki są prywatne, brak publicznych kolekcji.
- **Konto administratora** — Dlaczego zaparkowane: PRD §Non-Goals — jeden typ użytkownika w MVP.
- **Konfiguracja liczby powtórzeń EN** — Dlaczego zaparkowane: zdecydowano w PRD — hardkodowane 3 w MVP; konfiguracja w v2.
- **#1: Obsługa kolekcji i fiszek z przeglądarki Web** — Dlaczego zaparkowane: AGENTS.md §Hard Rules — MVP scope is Android only; webApp istnieje jako cel KMP, ale pełna funkcjonalność CRUD w przeglądarce planowana w v2.
- **#3: Okno opcji przed przystąpieniem do nauki** — Dlaczego zaparkowane: zawiera funkcje z Non-Goals — tryb nauki z algorytmem powtórkowym, tryb EN→PL (PRD §Non-Goals, p. „FR-015: Tryb EN→PL"), konfigurowalna liczba powtórzeń (PRD §Non-Goals, p. „Konfiguracja liczby powtórzeń EN"); implementacja możliwa w v2 równolegle z algorytmem powtórkowym.
- **#4: Zmiany UI okna nauki** — Dlaczego zaparkowane: przyciski „Wiem"/„Nie wiem" wymagają algorytmu powtórkowego (PRD §Non-Goals, p. „Spaced repetition"); wizualne wyświetlanie tekstu pytania/odpowiedzi jest sprzeczne z założeniem „wyłącznie audio" (PRD §Sokrates); w v2 razem z trybem wizualnym.
- **#5: Rozszerzenie na wiele języków** — Dlaczego zaparkowane: PRD §Non-Goals — MVP obsługuje wyłącznie parę PL↔EN; wielojęzyczność wymaga refaktoryzacji TTS, danych i UI; w v2.
- **#6: Automatyczne tłumaczenie przy wprowadzaniu fiszki** — Dlaczego zaparkowane: wymaga integracji z zewnętrznym API tłumaczącym (np. DeepL/Google Translate) — nowa zależność zewnętrzna poza zakresem MVP; w v2 razem z rozszerzeniem edytora fiszek.

## Zrobione

- **F-01: (fundament) fiszki zsynchronizowane lokalnie; tryb nauki nie wymaga internetu** — Zarchiwizowano 2026-05-27 → `context/archive/2026-05-27-f-01/`. Lekcja: —.
- **S-02: użytkownik może wybrać kolekcję, uruchomić tryb nauki i słyszeć fiszki przez TTS podczas biegu z ekranem wyłączonym, sterując przyciskami słuchawek (PLAY/PAUSE/NEXT/PREV), bez dostępu do internetu** — Zarchiwizowano 2026-05-27 → `context/archive/2026-05-27-s-02/`. Lekcja: —.
- **S-03: użytkownik instaluje APK, aplikacja łączy się z backendem na Render.com, Rafał przeprowadza pierwszą pełną sesję nauki podczas rzeczywistego biegu (30-60 min, ekran wyłączony, słuchawki Bluetooth)** — Zarchiwizowano 2026-05-27 → `context/archive/2026-05-27-s-03/`. Lekcja: —.
- **S-04 (A+B+C): aplikacja wygląda jak projekt graficzny z `.tmp/UI` — paleta Dawn Run, czcionki Bricolage + JetBrains Mono, layout tor biegowy; CollectionFormScreen, CardFormScreen, ProfileScreen, nawigacja bottom-tab** — Zrealizowano 2026-05-29. Faza D (backend dla zaślepek) pozostaje w backlogu. Lekcja: —.
- **S-04-A: (faza A) design system i komponenty wgrane, projekt kompiluje się z nową paletą** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-28-ui-reskin-design-system/`. Lekcja: —.
- **S-04-B: (faza B) 4 istniejące ekrany reskinowane; aplikacja wygląda jak projekt graficzny** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-28-ui-reskin-screens/`. Lekcja: —.
- **S-04-C: (faza C) 3 nowe ekrany (stub dane) + nawigacja bottom-tab** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-29-ui-reskin-new-screens/`. Lekcja: —.
- **S-04-D: (faza D) realne dane zamiast zaślepek: /me, lastStudied, progress, translate** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-29-ui-reskin-backend-stubs/`. Lekcja: —.
- **S-05: lista kolekcji bez menu + subtitle "N fiszek · X dni temu"; edycja/usuń kolekcji w widoku szczegółów; formularze nie zasłaniane przez klawiaturę** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-29-ui-tweaks/`. Lekcja: —.
- **S-06: przemianowanie pól fiszki — `polish_text`→`source_text`, `english_text`→`target_text` w DB, API i całym frontendzie** — Zarchiwizowano 2026-05-29 → `context/archive/2026-05-29-rename-flashcard-fields/`. Lekcja: —.
- **S-07: zmiany i optymalizacje w UI lub kodzie frontendu** — Zrealizowano 2026-05-29. Lekcja: —.
