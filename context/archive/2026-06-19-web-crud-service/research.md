---
date: 2026-06-19T00:00:00Z
researcher: Robert Karpiński
git_commit: 023182acf96a034559e9cb6b97f881ae270f6c60
branch: MVP
repository: FiszkiWBiegu
topic: "Serwis webowy KMP — zarządzanie kolekcjami i fiszkami z Google Sign-In"
tags: [research, web, wasm, compose-multiplatform, google-auth, koin]
status: complete
last_updated: 2026-06-19
last_updated_by: Robert Karpiński
---

# Research: Serwis webowy KMP — zarządzanie kolekcjami i fiszkami

**Date**: 2026-06-19  
**Git Commit**: `023182acf96a034559e9cb6b97f881ae270f6c60`  
**Branch**: MVP  
**Repository**: FiszkiWBiegu

## Research Question

W ramach frontend KMP należy napisać serwis webowy. Nie ma on trybu nauki, służy jedynie do zarządzania kolekcjami i fiszkami (dodawanie, edycja, usuwanie). Logowanie odbywa się tylko przez Google.

## Summary

**Co jest już gotowe (bez zmian):** Wszystkie ekrany CRUD (`CollectionsScreen`, `CollectionFormScreen`, `FlashcardsScreen`, `CardFormScreen`), ViewModels, repozytoria i `ApiClient` są w `commonMain` — działają na każdej platformie KMP łącznie z JS/WASM. `TokenStorage` używa `multiplatform-settings`, które na web korzysta z `localStorage`. Ktor client ma już target JS (`ktor-client-js`).

**Co wymaga implementacji:** Dwie rzeczy:
1. **Google Sign-In dla web** — `main.kt` wywołuje `App()` bez argumentu `onGoogleSignIn`, co defaultuje do `NotImplementedError`. Trzeba zaimplementować przez Google Identity Services JS API.
2. **Koin startup** — `main.kt` nie inicjalizuje Koin. Android robi to w klasie `Application`. Web musi wywołać `startKoin { modules(appModule) }` przed uruchomieniem kompozycji.

## Detailed Findings

### Istniejący webApp — stan wyjściowy

Lokalizacja: `apps/frontend/webApp/`

```
webApp/
├── build.gradle.kts          (JS + WASM targets, zależność od :shared)
└── src/webMain/
    ├── kotlin/.../main.kt    (entry point: ComposeViewport { App() })
    └── resources/
        ├── index.html        (loading spinner + webApp.js)
        └── styles.css        (body fullscreen)
```

`main.kt:11-14` — aktualny stan:
```kotlin
fun main() {
    ComposeViewport {
        App()   // ← onGoogleSignIn = domyślnie NotImplementedError
    }
}
```

`build.gradle.kts` — targety i zależności:
- `js { browser(); binaries.executable() }`
- `wasmJs { browser(); binaries.executable() }`
- `commonMain.dependencies { projects.shared; compose.ui }`

### Shared — co działa na web bez zmian

Wszystkie poniższe pliki są w `commonMain` i kompilują się na JS/WASM:

- `data/api/ApiClient.kt:18` — base URL `https://fiszkiwbiegu-server.xtaxi.eu`
- `data/api/TokenStorage.kt` — `multiplatform-settings` → `localStorage` na web ✓
- `data/api/AuthEventBus.kt` — SharedFlow, platform-agnostic ✓
- `data/repository/AuthRepository.kt` — `loginWithGoogle(idToken)` ✓
- `data/repository/CollectionRepository.kt` — pełny CRUD ✓
- `data/repository/FlashcardRepository.kt` — pełny CRUD ✓
- `screens/collections/CollectionsScreen.kt` — lista kolekcji ✓
- `screens/collections/CollectionFormScreen.kt` — formularz kolekcji ✓
- `screens/flashcards/FlashcardsScreen.kt` — lista fiszek ✓
- `screens/flashcards/CardFormScreen.kt` — formularz fiszki ✓
- `di/AppModule.kt` — Koin module z wszystkimi repozytoriami i ViewModelami ✓

`shared/build.gradle.kts` ma już:
```kotlin
jsMain.dependencies {
    implementation(libs.wrappers.browser)   // kotlin-browser wrappers
    implementation(libs.ktor.client.js)     // Ktor JS engine
}
wasmJsMain.dependencies {
    implementation(libs.ktor.client.js)
}
```

### Problem #1: Koin nie jest startowany w webApp

Na Androidzie Koin startuje w `AndroidApp : Application`:
```kotlin
startKoin { androidContext(this); modules(appModule, androidModule) }
```

W `webApp/main.kt` nie ma żadnej inicjalizacji Koin. `koinViewModel()` / `inject()` wywali się w runtime.

**Rozwiązanie:** Dodać do `main.kt`:
```kotlin
startKoin { modules(appModule, webModule) }
```

`webModule` to analogia `androidModule` — musi dostarczyć platform-specific bindingi. Jedynym kandydatem jest `LearningController` (potrzebne przez `LearningViewModel`). Na web można zarejestrować no-op implementację lub w ogóle nie rejestrować `LearningViewModel` (skoro Learning jest out-of-scope).

### Problem #2: Google Sign-In nie działa na web

`App.kt:66`:
```kotlin
fun App(
    onGoogleSignIn: suspend () -> Result<String> = { Result.failure(NotImplementedError()) },
    ...
)
```

Na Androidzie: `MainActivity` tworzy `GoogleSignInHelper` (CredentialManager API) i przekazuje callback.

Na web: odpowiednikiem jest **Google Identity Services (GIS)** — JavaScript SDK.

**Mechanizm:**
1. W `index.html` dodać: `<script src="https://accounts.google.com/gsi/client" async></script>`
2. Wywołać z Kotlin/JS: `google.accounts.id.initialize({ client_id: "...", callback: fn })` + `google.accounts.id.prompt()`
3. Callback JS otrzymuje `CredentialResponse { credential: String }` (to jest idToken JWT)
4. Przekazać idToken do `authRepository.loginWithGoogle(idToken)`

Implementacja Kotlin/JS (w `webMain/`):
```kotlin
// external declarations dla GIS API
external object google {
    val accounts: Accounts
}
external interface Accounts {
    val id: GoogleId
}
external interface GoogleId {
    fun initialize(config: dynamic)
    fun prompt()
    fun renderButton(parent: dynamic, config: dynamic)
}

// użycie w main.kt
fun webGoogleSignIn(): Result<String> {
    // suspendCancellableCoroutine + callback
}
```

Alternatywnie: Google One Tap flow przez `renderButton()` (widoczny przycisk "Sign in with Google").

**Client ID** (z `MainActivity.kt:14`):
```
71847229905-mqalk30tubb1pstdjq73krh0ovasqf2f.apps.googleusercontent.com
```

### Nawigacja — jak jest skonfigurowana

`App.kt:51-62` — sealed Routes:
```kotlin
sealed interface Route {
    data object Login : Route
    data object Collections : Route
    data class Flashcards(val collection: CollectionDto) : Route
    data class Learning(val collection: CollectionDto) : Route   // web: nieaktywne
    data object Profile : Route
    data class CollectionForm(val collection: CollectionDto? = null) : Route
    data class CardForm(val collection: CollectionDto, val flashcard: FlashcardDto? = null) : Route
}
```

Framework: `navigation3-compose` (`NavDisplay` + `entryProvider` + `mutableStateListOf<Route>`)

Na web: `Route.Learning` istnieje w shared, ale web po prostu nie udostępnia przycisku "Rozpocznij naukę" — ekran naturalnie odpada z nawigacji.

### expect/actual na web

`shared/src/commonMain/kotlin/.../Platform.kt`:
```kotlin
interface Platform { val name: String }
expect fun getPlatform(): Platform
```

Actual dla JS: `Platform.js.kt` — używa `navigator.userAgent`  
Actual dla WASM: `Platform.wasmJs.kt` — zwraca `"Web with Kotlin/Wasm"`

Brak innych expect/actual dotyczących web. `LearningController` jest interfejsem w `commonMain` — potrzebna web-actual (no-op).

### Backend API — kontrakty

URL: `https://fiszkiwbiegu-server.xtaxi.eu`  
Auth: `Authorization: Bearer <JWT>` (wszystkie poza `/auth/login`)  
JWT TTL: 30 dni, algorytm HS256

Endpointy używane przez web service:
```
POST /auth/login          { id_token: String } → { token: String }
GET  /auth/me             → UserDto
GET  /collections         → List<CollectionDto>
POST /collections         { name, description, source_language, target_language }
PUT  /collections/{id}    { name, description, source_language, target_language }
DELETE /collections/{id}
GET  /collections/{id}/flashcards → List<FlashcardDto>
POST /collections/{id}/flashcards { source_text, target_text }
PUT  /flashcards/{id}     { source_text?, target_text? }
DELETE /flashcards/{id}
```

Walidacje backendu:
- `name` nie może być puste (400)
- `source_language != target_language` (422)
- Dozwolone kody języków: `pl`, `en`, `de`, `es`, `fr`, `it`

## Code References

- `apps/frontend/webApp/src/webMain/kotlin/.../main.kt` — entry point (wymaga: Koin startup + onGoogleSignIn)
- `apps/frontend/webApp/src/webMain/resources/index.html` — wymaga: GIS script tag
- `apps/frontend/webApp/build.gradle.kts` — konfiguracja JS+WASM targetów
- `apps/frontend/shared/src/commonMain/kotlin/.../App.kt:66` — onGoogleSignIn callback
- `apps/frontend/shared/src/commonMain/kotlin/.../App.kt:129-135` — 401 → logout flow
- `apps/frontend/shared/src/commonMain/kotlin/.../di/AppModule.kt` — Koin module
- `apps/frontend/androidApp/src/main/kotlin/.../GoogleSignInHelper.kt` — Android reference impl
- `apps/frontend/androidApp/src/main/kotlin/.../MainActivity.kt:14` — Google Client ID
- `apps/frontend/shared/src/commonMain/kotlin/.../data/api/ApiClient.kt:18` — API base URL

## Architecture Insights

### Minimalne zmiany wystarczą

Ponieważ cały kod CRUD jest już w `commonMain`, implementacja web service sprowadza się do:

1. **`webApp/main.kt`** — dodać Koin startup + przekazać `onGoogleSignIn`
2. **`webApp/src/webMain/`** — dodać Google Sign-In impl (Kotlin/JS interop)
3. **`index.html`** — dodać GIS `<script>` tag
4. **`shared/di/`** — opcjonalnie `webModule` z no-op LearningController (jeśli potrzebne)

Nie ma potrzeby tworzenia nowych ekranów ani ViewModels.

### Kwestia expect/actual dla LearningController

`LearningController` (interfejs w `commonMain`) jest rejestrowany w Koin tylko przez `androidModule`. Jeśli `LearningViewModel` jest w `appModule`, Koin na web nie znajdzie `LearningController`. 

Rozwiązanie: albo usunąć `LearningViewModel` z `appModule` i przenieść do `androidModule`, albo dodać `webModule` z no-op implementacją.

### TokenStorage działa natywnie na web

`multiplatform-settings` na JS target używa `window.localStorage` — JWT token będzie persystowany między sesjami bez żadnych zmian.

### 401 Auto-logout

`App.kt:129-135` — `AuthEventBus.unauthorizedEvents` jest już obsługiwany w `App.kt`: przy 401 wylogowuje i przekierowuje na Login. Działa identycznie na web.

## Historical Context

- PRD (`context/foundation/prd.md`) — wyraźnie określa: webApp tylko CRUD, learning mode tylko Android
- Roadmap (`context/foundation/roadmap.md`) — S-01 (CRUD) jest `done`, S-02 (nauka) jest `done` (Android)
- Żadna z dotychczasowych 23 zmian w `context/changes/` nie dotyczyła web/WASM

## Open Questions

1. **Google Sign-In UX**: One Tap (overlay) vs. tradycyjny przycisk `renderButton()`? One Tap jest bardziej nowoczesny, przycisk jest prostszy.
2. **WASM vs JS**: Czy budujemy oba targety (JS + wasmJs) czy tylko jeden? WASM jest szybszy ale wymaga nowszej przeglądarki. Oba są skonfigurowane w build.gradle.kts.
3. **LearningController w webModule**: No-op stub czy całkowite usunięcie z `appModule` + przeniesienie do `androidModule`? Drugie jest czystsze architektonicznie.
4. **Hosting**: Gdzie deployować statyczne artefakty web? PRD wspomina "hosting statyczny" — Render static site, GitHub Pages, Vercel lub Cloudflare Pages.
5. **CORS**: Czy backend ma CORS skonfigurowany dla domeny docelowej? Aktualnie `actix-cors` jest ustawiony na `permissive` — dla MVP OK.
