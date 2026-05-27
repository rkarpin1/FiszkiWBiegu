<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-01 Cache i synchronizacja fiszek offline

- **Plan**: `context/changes/f-01/plan.md`
- **Scope**: All phases (1–2 of 2)
- **Date**: 2026-05-27
- **Verdict**: NEEDS ATTENTION
- **Findings**: 1 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — NetworkCallback nigdy nie jest wyrejestrowany

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, żeby to przemyśleć
- **Dimension**: Safety & Quality
- **Location**: `AndroidNetworkChecker.kt:24` (init block)
- **Detail**: `registerNetworkCallback` rejestruje callback w systemie Android, ale brak symetrycznego `unregisterNetworkCallback`. Koin singleton żyje przez cały czas aplikacji, więc w normalnym przepływie nie wycieka — problem ujawnia się jeśli: (a) testy reinicjalizują Koin, (b) `AndroidNetworkChecker` jest odtwarzany poza singletonem, (c) Android dump-sytem zgłasza osierocone callbacki (widoczne w `dumpsys connectivity`). Wzorzec z `AndroidLearningController` rozwiązuje to przez `releaseController()`.
- **Fix A ⭐ Recommended**: Zapisz callback do `val` i dodaj `fun close()` do interfejsu — symetryczne z `registerNetworkCallback`; wzorzec `Closeable`. Kompromis: wymaga zmiany interfejsu i wywołania `close()` np. przez Koin scope. Pewność: HIGH. Martwy punkt: `Application.onTerminate()` nie gwarantowany na urządzeniach.
- **Fix B**: Nie rób nic — singleton = czas życia procesu. OS czyści przy zabiciu. Kompromis: formalny wyciek widoczny w `dumpsys connectivity`; blokuje testy reinicjalizujące Koin. Pewność: MEDIUM.
- **Decision**: FIXED via Fix A — extracted `networkCallback` to `private val`, added `override fun release()` in `AndroidNetworkChecker`, added `fun release() {}` default to `NetworkChecker` interface, added `onTerminate()` in `FiszkiApplication`.

### F2 — `onCapabilitiesChanged` może dawać fałszywe `false` przy przełączaniu sieci

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Safety & Quality
- **Location**: `AndroidNetworkChecker.kt:31–33`
- **Detail**: `caps.hasCapability(NET_CAPABILITY_INTERNET)` sprawdza przekazaną przez callback sieć, nie `activeNetwork`. Przy przełączaniu WiFi↔LTE może wywołać `onCapabilitiesChanged` dla starej sieci z `INTERNET=false` przed `onAvailable` dla nowej — przycisk "▶ Nauka" chwilowo miga. `onLost` poprawnie wywołuje `isCurrentlyOnline()`; `onCapabilitiesChanged` powinno robić to samo.
- **Fix**: Zmień `_isOnline.value = caps.hasCapability(...)` na `_isOnline.value = isCurrentlyOnline()`.
- **Decision**: FIXED — zmieniono `onCapabilitiesChanged` na `_isOnline.value = isCurrentlyOnline()`.

### F3 — Brak `NET_CAPABILITY_VALIDATED` — captive portal da fałszywy pozytyw

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Safety & Quality
- **Location**: `AndroidNetworkChecker.kt:38–41` (isCurrentlyOnline)
- **Detail**: `NET_CAPABILITY_INTERNET` oznacza że sieć *deklaruje* dostęp do internetu. Hotelowe Wi-Fi przed logowaniem na captive portalu ma tę flagę = true, ale faktycznie blokuje ruch API. Biegacz na siłowni widzi aktywny przycisk "▶ Nauka", naciska, a sesja failuje. `NET_CAPABILITY_VALIDATED` jest flagą ustawianą przez Android po faktycznej weryfikacji połączenia.
- **Fix**: Dodaj `&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)` w `isCurrentlyOnline()`.
- **Decision**: FIXED — dodano `NET_CAPABILITY_VALIDATED` do `isCurrentlyOnline()`.

### F4 — Zapisy `StateFlow` z wątku OS callback — bezpieczne, ale nieudokumentowane

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: `AndroidNetworkChecker.kt:26, 29, 32`
- **Detail**: Callbacki `ConnectivityManager.NetworkCallback` wołane są z wątku Binder/Handler systemu, nie z wątku głównego. `MutableStateFlow.value = ...` jest thread-safe, a Compose `collectAsState()` poprawnie obsługuje wątki przez `Dispatchers.Main.immediate` — nie ma crashu. Jednak brak komentarza może zaskoczyć kolejnego autora.
- **Fix**: Dodaj komentarz: `// NetworkCallback invoked on a binder thread; StateFlow.value is thread-safe`
- **Decision**: FIXED — dodano komentarz w `AndroidNetworkChecker.kt`.

### F5 — `networkChecker` wstrzykiwany w composable zamiast w ViewModel

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🔎 MEDIUM — warto się zatrzymać; implikacje dla przyszłej rozbudowy
- **Dimension**: Architecture
- **Location**: `FlashcardsScreen.kt:44`
- **Detail**: `networkChecker` wstrzykiwany bezpośrednio w composable przez `koinInject()`. `FlashcardsViewModel` nie zna stanu sieci — nie może automatycznie ponowić `loadFlashcards()` przy powrocie sieci. Jeśli S-02 zechce automatycznego refreshu, trzeba będzie przenosić checker do ViewModelu. Obecna decyzja (tylko wyłączenie przycisku) jest świadoma.
- **Fix**: Brak wymaganej akcji — decyzja projektowa świadoma dla MVP.
- **Decision**: SKIPPED — świadoma decyzja MVP; refresh po powrocie sieci poza zakresem.

### F6 — `LearningService exported="true"` bez komentarza o intencji

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; nie wymaga akcji w tym PR
- **Dimension**: Safety & Quality
- **Location**: `AndroidManifest.xml:31`
- **Detail**: `exported="true"` wymagane przez Media3 `MediaSessionService`. Brak komentarza wyjaśniającego dlaczego — przyszły recenzent może oznaczyć jako błąd bezpieczeństwa.
- **Fix**: Dodaj XML komentarz: `<!-- exported required by MediaSessionService -->`
- **Decision**: FIXED — dodano komentarz z pełnym wyjaśnieniem (media button routing) w `AndroidManifest.xml`.

### F7 — `onAvailable` ustawia `true` bezpośrednio (nie przez `isCurrentlyOnline()`)

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — brak wymaganej akcji; informacyjne
- **Dimension**: Safety & Quality
- **Location**: `AndroidNetworkChecker.kt:26`
- **Detail**: W `onAvailable` ustawiamy `true` bezpośrednio, bo `activeNetwork` może jeszcze nie być zaktualizowane w momencie callbacku. To świadome i właściwe podejście — niespójne z `onLost`/`onCapabilitiesChanged` z zamysłu.
- **Fix**: Brak wymaganej akcji.
- **Decision**: SKIPPED — `onAvailable` ustawia `true` bezpośrednio z zamysłu; `activeNetwork` może być nieaktualne w momencie callbacku.
