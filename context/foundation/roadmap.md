---
project: "FiszkiWBiegu"
version: 1
status: draft
created: 2026-05-27
updated: 2026-05-27
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
| S-03 | production-run-validation      | zainstalować APK i przeprowadzić pełną sesję nauki podczas rzeczywistego biegu    | S-02              | US-01, FR-012                                     | proposed |

## Strumienie

Pomoc nawigacyjna — grupuje elementy, które dzielą łańcuch wymagań wstępnych. Kanoniczna kolejność nadal znajduje się w grafie zależności poniżej; ta tabela to proponowana kolejność czytania równoległych ścieżek.

| Strumień | Temat                   | Łańcuch                           | Uwaga                                                                     |
| -------- | ----------------------- | --------------------------------- | ------------------------------------------------------------------------- |
| A        | Offline i sesja nauki   | `F-01` → `S-02` → `S-03`         | Ścieżka do gwiazdy przewodniej; F-01 jest kluczowym odblokowanym elementem. |
| B        | CRUD i uwierzytelnianie | `S-01` → (dołącza do A w `S-02`) | Równolegle z F-01; razem z F-01 odblokowuje S-02.                         |

## Baza

Co już jest na miejscu w bazie kodu na dzień 2026-05-27 (automatycznie zbadane + potwierdzone przez użytkownika).
Fundamenty poniżej zakładają, że te elementy są obecne i NIE tworzą ich ponownie.

- **Frontend:** obecny — Compose Multiplatform + Material3; routing (App.kt:19-24); wszystkie 4 ekrany: LoginScreen, CollectionsScreen, FlashcardsScreen, LearningScreen; moduły androidApp i webApp
- **Backend / API:** obecny — Actix-web 4.13; pełne CRUD dla kolekcji i fiszek; POST /auth/login, GET /collections, PUT/DELETE /flashcards/{id}; auth JWT
- **Dane:** częściowy — sqlx + PostgreSQL (backend), migracje 001_init + 002_add_users, tabele: users/collections/flashcards; BRAK lokalnego cache fiszek we frontendzie (dane sieciowe); token persistowany przez multiplatform-settings
- **Autoryzacja:** obecna — Google OAuth 2.0 (auth.rs:16), JWT create/verify (auth.rs:31-82), AuthUser extractor na trasach; LoginScreen + GoogleSignInHelper.kt (Android Credential Manager)
- **Wdrożenie / infra:** częściowe — GitHub Actions CI (wyłącznie budowanie APK Android); backend: auto-deploy via Render.com (render.yaml); BRAK Dockerfile
- **Obserwowalność:** częściowa — brak ujednoliconego systemu logowania; ad-hoc eprintln w backend; brak Sentry / Crashlytics

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
- **Ryzyko:** Kod warstw auth + CRUD + UI jest obecny (GoogleSignInHelper.kt, CollectionsScreen, FlashcardsScreen), ale E2E integracja mogła nie być testowana kompleksowo; problemy z wiringiem mogą się ujawnić przy pierwszym pełnym przejściu przepływu.
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
- **Status:** proposed

## Przekazanie do backlogu

| ID mapy drogowej | ID zmiany                      | Sugerowany tytuł problemu                                     | Gotowe do `/10x-plan` | Uwagi                                                              |
| ---------------- | ------------------------------ | ------------------------------------------------------------- | --------------------- | ------------------------------------------------------------------ |
| F-01             | offline-flashcard-cache        | Cache i synchronizacja fiszek offline (Android Room/SQLite)  | yes                   | Uruchom `/10x-plan offline-flashcard-cache`                        |
| S-01             | collections-flashcards-e2e    | Zarządzanie kolekcjami i fiszkami — weryfikacja E2E           | done                  | Zrealizowane                                                       |
| S-02             | audio-learning-session-offline | Tryb nauki audio offline — integracja i testy                 | no                    | Czeka na ukończenie F-01 + S-01                                    |
| S-03             | production-run-validation      | Wdrożenie produkcyjne + pierwsza sesja na żywo                | no                    | Czeka na S-02; skonfiguruj Render env vars wcześniej               |

## Otwarte pytania dotyczące mapy drogowej

1. **Synchronizacja fiszek offline** — kiedy i jak aplikacja synchronizuje fiszki z backendu na urządzenie? (przy uruchomieniu / przy starcie sesji nauki / w tle / ręcznie). Właściciel: Rafał. Blokada: F-01 (decyzja architektoniczna do podjęcia w `/10x-plan offline-flashcard-cache`).
2. **Długość pauz w cyklu audio** — ile sekund po tekście PL (przed EN) i między powtórzeniami EN? Właściciel: Rafał. Blokada: S-02 (decyzja empiryczna podczas implementacji — nie blokuje startu, ale musi być ustalona przed finalizacją UX trybu nauki).

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
