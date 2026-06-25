---
date: 2026-06-25
researcher: Robert Karpiński
git_commit: 2fd68f676b673592dc7b28fac52420ced67a1f01
branch: MVP
repository: FiszkiWBiegu
topic: "Ekran kolekcji „zawisa" (nieskończony spinner) po dodaniu nowej fiszki"
tags: [research, codebase, flashcards, networking, ktor, viewmodel, loading-state]
status: complete
last_updated: 2026-06-25
last_updated_by: Robert Karpiński
---

# Research: Ekran kolekcji „zawisa" po dodaniu nowej fiszki

**Date**: 2026-06-25 (Europe/Warsaw)
**Researcher**: Robert Karpiński
**Git Commit**: 2fd68f676b673592dc7b28fac52420ced67a1f01
**Branch**: MVP
**Repository**: FiszkiWBiegu

## Research Question

Zdarza się, że po wprowadzeniu nowej fiszki, okno z kolekcją (FlashcardsScreen) „zawisa". Widać, że kolekcja odświeża się, ale trwa to „wiecznie". Dlaczego?

## Summary

**Przyczyna źródłowa:** zawieszony `GET /collections/{id}/flashcards` zostawia `isLoading = true` na zawsze, bo:

1. Po udanym dodaniu fiszki `FlashcardsViewModel.createCard()` wywołuje `loadFlashcards()`, które ustawia `isLoading = true` i **resetuje je wyłącznie wewnątrz `.fold()`** — nie ma `try/finally` ani `withTimeout`. Jeśli `repo.getAll()` nigdy nie wróci, żadna gałąź `fold` się nie wykona, a spinner (`FlashcardsScreen.kt:365`) kręci się bez końca.
2. Klient Ktor **nie ma skonfigurowanego żadnego timeoutu** (`HttpTimeout` nieobecny) — żądanie może wisieć w nieskończoność, bez automatycznego odzyskania ani retry.
3. Backend stoi na Render (`https://fiszkiwbiegu-server.xtaxi.eu`), który ma udokumentowany cold-start 30–60 s na darmowym planie — to wyjaśnia charakter „**zdarza się**" / „trwa wiecznie".

**Co wykluczono:** hipotezę, że nawigacja powrotna anuluje coroutine `createCard`. `CardFormScreen` i `FlashcardsScreen` współdzielą **tę samą instancję** `FlashcardsViewModel` (Activity ViewModelStore — `NavDisplay` nie instaluje per-entry `ViewModelStore`), więc pop ekranu formularza niczego nie czyści; `viewModelScope` żyje, a `loadFlashcards()` działa dalej. Problemem jest więc zawisanie żądania, nie anulowanie.

To dokładnie ten sam wzorzec defektu, co w archiwum `learning-start-bug` i `f-01`: **`Result`/`isLoading` zależne wyłącznie od powrotu wywołania sieciowego, bez timeoutu i bez ścieżki odzyskania.**

## Detailed Findings

### 1. Przepływ: dodanie fiszki → `createCard` → `loadFlashcards` → spinner

- `CardFormScreen` zapisuje fiszkę: `onSave` → `viewModel.createCard(newFlashcard)` a zaraz potem `onBack()` (`CardFormScreen.kt:87-91`).
- `createCard` uruchamia coroutine, a po sukcesie woła `loadFlashcards()` (`FlashcardsViewModel.kt:80-87`):
  ```kotlin
  fun createCard(flashcard: FlashcardDto) {
      viewModelScope.launch {
          repo.create(collectionId, flashcard.sourceText, flashcard.targetText).fold(
              onSuccess = { loadFlashcards() },
              onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
          )
      }
  }
  ```
- Spinner na `FlashcardsScreen` jest sterowany wyłącznie przez `uiState.isLoading` (`FlashcardsScreen.kt:365-373`). `createCard` sam **nie** ustawia `isLoading`; spinner pojawia się dopiero, gdy `loadFlashcards()` ustawi `isLoading = true` — stąd obserwacja „widać, że kolekcja się odświeża".
- W `FlashcardsScreen`/`FlashcardsScreenContent` **nie ma** żadnego `LaunchedEffect` re-triggerującego ładowanie — to nie jest pętla odświeżania, lecz jeden zawieszony load (`FlashcardsScreen.kt:81-103`, `:105-116`).

### 2. Mechanizm utknięcia stanu ładowania (`isLoading` poza `finally`)

`FlashcardsViewModel.loadFlashcards()` (`FlashcardsViewModel.kt:34-56`):
```kotlin
fun loadFlashcards() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }   // :36
        repo.getAll(collectionId).fold(
            onSuccess = { list -> _uiState.update { it.copy(flashcards = list, isLoading = false) } }, // :42
            onFailure = { e   -> _uiState.update { it.copy(error = e.message, isLoading = false) } },   // :49
        )
    }
}
```
- `isLoading = false` jest ustawiane **tylko** w `onSuccess`/`onFailure`. Brak `try { } finally { isLoading = false }` i brak `withTimeout(...)`.
- Jeśli `repo.getAll(collectionId)` (suspend) nigdy nie wróci, `fold` nigdy się nie wykona → `isLoading` pozostaje `true` → spinner bez końca.
- Identyczny wzorzec ma `CollectionsViewModel.loadCollections()` (`CollectionsViewModel.kt:34-58`).

### 3. Brak timeoutów w kliencie Ktor

`ApiClient` tworzy `HttpClient` z **tylko** `ContentNegotiation` i `HttpCallValidator` (obsługa 401) — `ApiClient.kt:24-35`:
```kotlin
private val client = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpCallValidator) { validateResponse { /* 401 -> authEventBus */ } }
}
```
- **Brak `HttpTimeout`** (request/connect/socket) — potwierdzone grepem po `shared/src` (0 trafień timeout-ów) i analizą wszystkich silników (`ktor-client-android`, `ktor-client-js`, `ktor-client-darwin`).
- **Brak `HttpRequestRetry`.**
- Wszystkie wywołania to gołe `client.get/post/...` bez `timeout { }` per-request (`ApiClient.kt:37-101`), m.in. `getFlashcards()` (`:57-58`) i `createFlashcard()` (`:60-65`).
- Konsekwencja: zawieszone połączenie (martwy keep-alive, cold-start, zerwane TCP bez RST) wisi w nieskończoność, bo nic go nie przerywa.

### 4. Współdzielenie ViewModelu — dlaczego anulowanie NIE jest przyczyną

- Oba ekrany wołają `koinViewModel(key = collection.id) { parametersOf(collection.id) }` — `FlashcardsScreen.kt:84`, `CardFormScreen.kt:74`.
- `NavDisplay` (navigation3-ui 1.1.1) domyślnie instaluje **tylko** `rememberSaveableStateHolderNavEntryDecorator()` — **nie** `rememberViewModelStoreNavEntryDecorator()`; `App.kt:160` nie przekazuje własnych `entryDecorators`. NavDisplay dostarcza per-scene jedynie `LocalLifecycleOwner`, nigdy `LocalViewModelStoreOwner`.
- `koin-compose-viewmodel` (koin 4.2.2) rozwiązuje `LocalViewModelStoreOwner.current`, którym na Androidzie jest **Activity** (z `setContent`). `key` jest tylko nazwą slotu **w obrębie jednego store**.
- **Wniosek:** oba ekrany dostają **tę samą** instancję `FlashcardsViewModel`. Pop `CardForm` (`onBack`) nie czyści żadnego store, `onCleared()` się nie wykonuje, `viewModelScope` nie jest anulowany. Coroutine `createCard`/`loadFlashcards` działa dalej.
- DI: `AppModule.kt:28` → `viewModel { params -> FlashcardsViewModel(get(), params.get()) }`.
- ⚠️ **Regresja do uniknięcia:** gdyby ktoś dodał `rememberViewModelStoreNavEntryDecorator()` do `entryDecorators` w `NavDisplay`, każdy entry dostałby własny store → różne instancje → pop `CardForm` anulowałby coroutine `createCard` (klasyczny footgun nav3). Warto zostawić komentarz ostrzegawczy.

### 5. `runCatching` połyka `CancellationException` (utajony, nie jest przyczyną tutaj)

- `FlashcardRepository.getAll()` opakowuje wywołanie w `runCatching { }` (`FlashcardRepository.kt:13-17`); analogicznie `CollectionRepository` (`:15-19`).
- `runCatching` łapie **każdy** `Throwable`, w tym `kotlinx.coroutines.CancellationException` — łamie strukturalną współbieżność i może maskować anulowanie. W tym konkretnym błędzie to nie przyczyna (VM nie jest anulowany), ale to realna pułapka do naprawy przy okazji (rethrow `CancellationException`).

### 6. Backend na Render → charakter „zdarza się"

- `API_BASE_URL = "https://fiszkiwbiegu-server.xtaxi.eu"` (`ApiClient.kt:18`) — instancja Render.
- Archiwum dokumentuje cold-start: „*Render.com bezpłatny plan zasypia po 15 min nieaktywności — cold start 30-60 sek*" (`context/archive/2026-05-27-s-03/plan-brief.md:11`). W połączeniu z brakiem timeoutu cold-start objawia się jako wiszące żądanie — silny kandydat na „sometimes".

## Code References

- `apps/frontend/shared/.../screens/flashcards/FlashcardsViewModel.kt:34-56` — `loadFlashcards()`: `isLoading` resetowane tylko w `fold`, brak `finally`/`withTimeout` (sedno)
- `apps/frontend/shared/.../screens/flashcards/FlashcardsViewModel.kt:80-87` — `createCard()` → `loadFlashcards()` po sukcesie
- `apps/frontend/shared/.../screens/flashcards/FlashcardsScreen.kt:365-373` — spinner sterowany `uiState.isLoading`
- `apps/frontend/shared/.../screens/flashcards/FlashcardsScreen.kt:397-404` — istniejący Snackbar z akcją „Ponów" (`onLoadFlashcards`) — gotowa ścieżka odzyskania, dziś nieaktywowana przy zawisie
- `apps/frontend/shared/.../screens/flashcards/CardFormScreen.kt:87-91` — `onSave` → `createCard` + `onBack`
- `apps/frontend/shared/.../data/api/ApiClient.kt:24-35` — `HttpClient` bez `HttpTimeout`/retry
- `apps/frontend/shared/.../data/api/ApiClient.kt:18` — `API_BASE_URL` (Render)
- `apps/frontend/shared/.../data/api/ApiClient.kt:57-58,60-65` — `getFlashcards`/`createFlashcard` bez per-request timeout
- `apps/frontend/shared/.../data/repository/FlashcardRepository.kt:13-17` — `getAll` w `runCatching` (połyka `CancellationException`)
- `apps/frontend/shared/.../App.kt:160` — `NavDisplay` bez `entryDecorators`; `:206-225` entry Flashcards; `:233-239` entry CardForm
- `apps/frontend/shared/.../di/AppModule.kt:28` — rejestracja `FlashcardsViewModel`
- `apps/frontend/shared/.../screens/collections/CollectionsViewModel.kt:34-58` — ten sam wzorzec `isLoading` (drugie miejsce)

## Architecture Insights

- **Jeden punkt kontroli stanu ładowania na warstwę VM, bez gwarancji domknięcia.** Każda VM ustawia `isLoading=true` i polega na powrocie wywołania sieciowego. Bez timeoutu w warstwie transportu (Ktor) ani siatki bezpieczeństwa w VM (`finally`/`withTimeout`), dowolny zawis sieci = trwały spinner. To wzorzec systemowy, nie lokalny — dotyczy `FlashcardsViewModel` i `CollectionsViewModel`.
- **UI odzyskiwania już istnieje, ale jest nieosiągalne przy zawisie.** Snackbar „Ponów" (`FlashcardsScreen.kt:397-404`) pojawia się tylko gdy `uiState.error != null` — a przy wiszącym żądaniu `error` nigdy nie jest ustawiany. Wystarczy, by zawis kończył się `failure`, żeby ta ścieżka zadziałała.
- **Współdzielony, długo żyjący VM.** `FlashcardsViewModel(key=collection.id)` żyje w Activity ViewModelStore przez całą sesję aplikacji (czyszczony dopiero przy zniszczeniu Activity), więc `viewModelScope` praktycznie nigdy nie jest anulowany podczas zwykłej nawigacji — to dobre dla odświeżania listy, ale oznacza, że „self-healing" przez anulowanie nie nastąpi.

## Historical Context (from prior changes)

- `context/archive/2026-06-19-learning-start-bug/research.md` — ten sam rodzaj defektu: „*`Result` chained only through `onSuccess {}` with no `onFailure`*"; druga przyczyna to pętla bez timeoutu (`while (!ttsReady) delay(100ms)`). Naprawiono przez **usunięcie kruchego drugiego wywołania i cichego guardu**, nie przez dodanie UI błędu.
- `context/archive/2026-05-27-f-01/plan.md:9` — dosłownie: „*Gdy offline — `Result.failure` jest ignorowane przez `onSuccess {}`, ekran nauki pokazuje `CircularProgressIndicator` bez końca.*" F-01 świadomie **nie** dodał spinner/error UI — tylko wyłączył przycisk wejścia (`enabled = ... && isOnline`), zostawiając wzorzec nieskończonego spinnera.
- `context/archive/2026-05-27-s-03/plan-brief.md:11` — Render cold-start 30–60 s (mitigacja: płatny plan, health-check < 3 s).
- `context/archive/2026-05-29-ui-reskin-new-screens/plan.md:14,28` — potwierdza zamierzony wzorzec: VM współdzielony przez Activity ViewModelStore, więc lista „odświeży się po powrocie z formularza" — dokładnie ten mechanizm, który tu zawisa, gdy `loadFlashcards` nie wraca.
- `context/archive/2026-05-29-ui-tweaks/plan.md:112-113` — ustalona konwencja mutacji: `onSuccess = { loadCollections() }, onFailure = { error = e.message }`.

## Related Research

- `context/archive/2026-06-19-learning-start-bug/research.md` — najbliższy analog (silent failure + brak timeoutu)
- `context/archive/2026-06-22-flashcard-translate/research.md` — opis `FlashcardsViewModel` (`:13-18`, `:32-54`) i wzorca `fold`
- `context/archive/2026-06-19-study-time-tracking/research.md` — przepływ `markStudied`/refresh `studyCompleted`

## Recommended Fix Options (do decyzji w fazie planowania)

1. **Najważniejsze — `HttpTimeout` na kliencie Ktor** (`ApiClient.kt:24`). Ustawić `requestTimeoutMillis` (np. 45–60 s, by zmieścić cold-start Render), `connectTimeoutMillis` (~15 s), `socketTimeoutMillis` (~30 s). Skutek: zawieszony `GET` zakończy się `failure` → `onFailure` → `isLoading=false` + pojawi się istniejący snackbar „Ponów". Najmniejsza, najwyżej-dźwigniowa zmiana.
2. **Siatka bezpieczeństwa w VM** (defense-in-depth): owinąć ładowanie w `try/finally { isLoading=false }` lub `withTimeout`, w `FlashcardsViewModel.loadFlashcards()` i `CollectionsViewModel.loadCollections()`.
3. **`runCatching` nie może połykać `CancellationException`** — w repozytoriach dodać rethrow (`getOrElse { if (it is CancellationException) throw it; ... }` albo jawne `try/catch`).
4. **Opcjonalnie `HttpRequestRetry`** dla przejściowych błędów cold-startu (np. 1–2 próby z backoffem).
5. **Opcjonalnie UX:** `createCard` mogłoby od razu ustawiać `isLoading=true` (jak `confirmDelete`), by feedback był natychmiastowy.
6. **Operacyjnie:** utrzymać instancję Render „ciepłą" (płatny plan / ping) — mitigacja już rekomendowana w archiwum, ale to nie zastępuje timeoutu klienta.

## Open Questions

- Czy zawis dotyczy fazy `GET /flashcards`, czy czasem już `POST /flashcards` (create)? Logi sieci/timestamp z urządzenia rozstrzygnęłyby; analiza kodu wskazuje, że spinner pochodzi z `loadFlashcards` (GET), bo `createCard` sam nie pokazuje spinnera.
- Jakie wartości timeoutów są akceptowalne wobec cold-startu Render (kompromis: zbyt krótki timeout = fałszywe błędy przy zimnym serwerze; zbyt długi = długie „wiszenie" zanim pojawi się „Ponów")?
- Czy zastosować poprawkę także do pozostałych ekranów ładujących (Collections, Learning) w ramach tej samej zmiany, czy osobno?
