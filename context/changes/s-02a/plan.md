# Automatyczne wylogowanie przy wygaśnięciu tokenu (401 interceptor) — Plan implementacji

## Przegląd

Dodać Ktor `HttpCallValidator` w `ApiClient`, który na odpowiedź 401 emituje zdarzenie do nowej klasy `AuthEventBus`. `App.kt` obserwuje zdarzenia przez `LaunchedEffect` i wykonuje cichy logout → nawigacja do `LoginScreen`.

## Analiza stanu obecnego

`ApiClient` ma guard `requireToken()` (brak tokenu → wyjątek), ale nie obsługuje wygaśnięcia tokenu (JWT 30-day). Gdy token jest nieważny, backend zwraca HTTP 401. Repozytoria wywołują `error("HTTP ${response.status.value}")`, ViewModel emituje błąd do Snackbar — użytkownik utknął w pętli błędów bez możliwości wyjścia.

Brak jakichkolwiek wzorców SharedFlow/EventBus w codebase; `LaunchedEffect` jest stosowany w `LearningScreen.kt:40`. `authRepository.logout()` to non-suspend local call.

## Pożądany stan końcowy

Gdy API zwróci 401 (wygasły/nieważny token), aplikacja automatycznie: czyści token, wraca na `LoginScreen` — bez komunikatu, bez pętli błędów. Zachowanie identyczne z "zimnym startem" przy braku tokenu.

### Kluczowe odkrycia

- `ApiClient.kt:19` — `HttpClient { install(ContentNegotiation) { ... } }` — jeden istniejący plugin; wzorzec instalacji jest znany
- `AppModule.kt:18` — `single { ApiClient(get()) }` — do aktualizacji o drugi argument
- `App.kt:59` — `onLogout = { authRepository.logout(); destination = Destination.Login }` — dokładnie ta sama logika co wymagana po 401
- `AuthRepository.logout()` — non-suspend, `tokenStorage.clearToken()`

## Czego NIE robimy

- Komunikat "Sesja wygasła" — cichy redirect
- Obsługa 403 (Forbidden) — tylko 401
- Automatyczne odświeżanie tokenu (token refresh) — poza zakresem MVP
- Zmiany w backendzie

## Krytyczne szczegóły implementacji

**validateResponse NIE rzuca wyjątku.** Jeśli `validateResponse` rzuci, Ktor zamienia odpowiedź na wyjątek i repozytoria nie zobaczą statusu 401 — mogą reagować nieprzewidywalnie. Emitujemy zdarzenie i wracamy normalnie; repozytorium widzi `!response.status.isSuccess()` → `error("HTTP 401")` (szybko zastąpione przez LoginScreen).

**Miejsce LaunchedEffect w App.kt.** `LaunchedEffect(Unit)` musi być poza blokiem `when (val dest = destination)`, aby nie był restartowany przy każdej nawigacji. Poprawne miejsce: wewnątrz `MaterialTheme { }`, przed `when`.

## Faza 1: AuthEventBus + interceptor w ApiClient

### Przegląd

Stworzyć `AuthEventBus` jako singleton Koin, wstrzyknąć do `ApiClient`, zainstalować `HttpCallValidator` wykrywający 401.

### Wymagane zmiany

#### 1. Nowy plik: AuthEventBus

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/AuthEventBus.kt`

**Cel**: Shared event bus dla zdarzeń autoryzacji między warstwami data i UI. Singleton w Koin.

**Kontrakt**:
```
class AuthEventBus {
    val unauthorizedEvents: SharedFlow<Unit>
    suspend fun emitUnauthorized()
}
```
Implementacja: `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` — bufor 1 gwarantuje, że `emit()` nie zawiesza gdy kolektor jeszcze nie gotowy.

#### 2. Modyfikacja: ApiClient

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiClient.kt`

**Cel**: Wykrywać odpowiedź 401 i emitować zdarzenie do `AuthEventBus`.

**Kontrakt**: Nowy parametr konstruktora `authEventBus: AuthEventBus`. W bloku `HttpClient { ... }` dodać plugin Ktor `HttpCallValidator` z `validateResponse { if (response.status.value == 401) authEventBus.emitUnauthorized() }`.

#### 3. Modyfikacja: AppModule

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/di/AppModule.kt`

**Cel**: Zarejestrować `AuthEventBus` jako singleton; zaktualizować wstrzyknięcie `ApiClient`.

**Kontrakt**: Przed linią `single { ApiClient(get()) }` dodać `single { AuthEventBus() }`. Zaktualizować `single { ApiClient(get(), get()) }` — pierwsze `get()` = `TokenStorage`, drugie = `AuthEventBus`.

### Kryteria sukcesu

#### Weryfikacja automatyczna

- Kod kompiluje się bez błędów: `./gradlew :shared:test`

#### Weryfikacja ręczna

- Brak na tym etapie — integracja z UI w Fazie 2

---

## Faza 2: Wiring App.kt + weryfikacja E2E

### Przegląd

Wstrzyknąć `AuthEventBus` do `App.kt` przez Koin; dodać `LaunchedEffect` obserwujący `unauthorizedEvents`; na zdarzenie wywołać `authRepository.logout()` i przejść do `Destination.Login`.

### Wymagane zmiany

#### 1. Modyfikacja: App.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Obserwować zdarzenia 401 przez cały cykl życia aplikacji i wykonywać cichy logout.

**Kontrakt**:
- Dodać `val authEventBus: AuthEventBus = koinInject()` po `val authRepository`.
- Wewnątrz `MaterialTheme { }`, przed `when (val dest = destination)`, dodać:
  ```
  LaunchedEffect(Unit) {
      authEventBus.unauthorizedEvents.collect {
          authRepository.logout()
          destination = Destination.Login
      }
  }
  ```
- Dodać import `pl.rkarpinski.fiszkiwbiegu.data.api.AuthEventBus` i `androidx.compose.runtime.LaunchedEffect`.

### Kryteria sukcesu

#### Weryfikacja automatyczna

- APK buduje się: `JAVA_HOME="C:/Users/rkarp/.jdks/jbrsdk_jcef-17.0.14" ./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna

- Zalogować się w aplikacji (weryfikacja: ekran kolekcji widoczny)
- W Android Studio → App Inspection → App Data → SharedPreferences → zmienić wartość `auth_token` na `invalid_token`
- Wrócić do aplikacji i wykonać operację (np. pull-to-refresh lub nawigacja)
- Oczekiwany wynik: aplikacja automatycznie przechodzi na `LoginScreen` bez wyświetlania komunikatu błędu
- Zalogować się ponownie → weryfikacja, że app działa normalnie po ponownym logowaniu

---

## Strategia testowania

Brak testów jednostkowych w tym zakresie — obecny codebase nie ma testów dla warstwy HTTP. Weryfikacja przez E2E na urządzeniu (Faza 2).

## Referencje

- `context/changes/s-01/reviews/plan-review.md` — źródło F1, który zrodził tę zmianę
- `ApiClient.kt:19` — istniejący wzorzec instalacji pluginu Ktor
- `App.kt:59` — istniejąca logika logout w `onLogout`
- `LearningScreen.kt:40` — istniejący wzorzec `LaunchedEffect(Unit)`

## Progress

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Phase 1: AuthEventBus + interceptor w ApiClient

#### Automatyczne

- [x] 1.1 Kod kompiluje się bez błędów: `./gradlew :shared:test`

#### Ręczne

- [x] 1.2 Brak weryfikacji ręcznej na tym etapie

### Phase 2: Wiring App.kt + weryfikacja E2E

#### Automatyczne

- [ ] 2.1 APK buduje się: `./gradlew :androidApp:assembleDebug`

#### Ręczne

- [ ] 2.2 Zalogować się → zmienić `auth_token` na `invalid_token` via App Inspection → operacja → cichy redirect do LoginScreen
- [ ] 2.3 Zalogować się ponownie po 401-redirect → app działa normalnie
