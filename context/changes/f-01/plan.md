# F-01: Cache i synchronizacja fiszek offline — Plan implementacji

## Przegląd

Dodajemy wykrywanie łączności sieciowej i blokujemy przycisk "▶ Nauka" gdy urządzenie jest offline. Przepływ offline już działa poprawnie — po starcie sesji LearningService trzyma fiszki w pamięci i TTS nie wymaga sieci. F-01 domyka NFR przez blokadę startu gdy brak połączenia (brak sieci = brak możliwości załadowania fiszek).

## Analiza stanu obecnego

- `LearningViewModel.startSession()` wywołuje `repo.getAll(collectionId)` (API call). Gdy offline — `Result.failure` jest ignorowane przez `onSuccess {}`, ekran nauki pokazuje `CircularProgressIndicator` bez końca.
- `FlashcardsScreen` (linia 73–76) blokuje "▶ Nauka" tylko gdy `uiState.flashcards.isEmpty()`. Nie uwzględnia łączności.
- `androidModule` (AndroidModule.kt:8-10) — wzorzec dla platform-specific singletonów: interfejs commonMain, implementacja androidApp.
- Jedyny istniejący mechanizm offline: `LearningService` przechowuje fiszki w pamięci po starcie — NFR "po uruchomieniu sesji" jest już spełnione.

## Pożądany stan końcowy

Po zakończeniu tego planu: przycisk "▶ Nauka" w `FlashcardsScreen` jest wyłączony gdy urządzenie jest offline, a ponownie aktywuje się po odzyskaniu połączenia (reaktywna aktualizacja UI). Raz uruchomiona sesja nauki kontynuuje działanie bez sieci — bez zmian względem obecnego zachowania.

### Kluczowe odkrycia:

- `LearningController` to wzorzec do naśladowania: interfejs w `commonMain/LearningController.kt`, implementacja `androidApp/AndroidLearningController.kt`, wiring w `androidApp/di/AndroidModule.kt:9` — `NetworkChecker` idzie dokładnie tą samą ścieżką.
- `FlashcardsScreen.kt:74-76` — docelowe miejsce blokady: `TextButton(onClick = onStartLearning, enabled = uiState.flashcards.isNotEmpty())`. Wystarczy dodać `&& isOnline`.
- `FiszkiApplication.kt:12-15` — oba moduły (`appModule + androidModule`) są już dołączone.

## Czego NIE robimy

- Brak persystencji fiszek na dysku (Room, SQLDelight, multiplatform-settings + JSON) — user potwierdził że in-memory wystarczy dla MVP.
- Brak cache per kolekcja ani żadnego TTL.
- Brak obsługi offline CRUD (dodaj/edytuj/usuń fiszki offline).
- Brak wskaźnika "ostatnia synchronizacja" ani "tryb offline" w UI.
- Brak nowego UI w `LearningScreen` (spinner/błąd przy nieosiągalnym API).

## Podejście do implementacji

Wzorzec interfejs + implementacja platformowa — identyczny jak `LearningController`. Interfejs `NetworkChecker` w `commonMain` eksponuje `StateFlow<Boolean>`, implementacja Android używa `ConnectivityManager.registerNetworkCallback` inicjalizowanego w Koin singleton. `FlashcardsScreen` wstrzykuje `NetworkChecker` przez `koinInject()` i kolekcjonuje `isOnline`.

---

## Faza 1: NetworkChecker — interfejs + implementacja Android + Koin

### Przegląd

Dodaje reaktywne wykrywanie łączności jako Koin singleton dostępny we wszystkich composables.

### Wymagane zmiany:

#### 1. Interfejs NetworkChecker

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/NetworkChecker.kt`

**Cel**: Zdefiniować kontrakt platformowo-niezależny, analogiczny do `LearningController.kt`.

**Kontrakt**:
```kotlin
interface NetworkChecker {
    val isOnline: StateFlow<Boolean>
}
```

#### 2. Implementacja Android

**Plik**: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/AndroidNetworkChecker.kt`

**Cel**: Implementacja oparta o `ConnectivityManager` — rejestruje callback sieciowy przy inicjalizacji (singleton, czas życia = czas życia aplikacji) i aktualizuje `StateFlow` przy każdej zmianie łączności.

**Kontrakt**:
```kotlin
class AndroidNetworkChecker(context: Context) : NetworkChecker {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _isOnline.value = true }
            override fun onLost(network: Network) { _isOnline.value = isCurrentlyOnline() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

`onLost` używa `isCurrentlyOnline()` (nie `false`) — utrata jednej sieci (WiFi) nie oznacza braku internetu gdy aktywne mobilne dane.

#### 3. Koin wiring

**Plik**: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/di/AndroidModule.kt`

**Cel**: Zarejestrować `AndroidNetworkChecker` jako singleton pod interfejsem `NetworkChecker` — wzorzec linia 9 (LearningController).

**Kontrakt**: Dodać jeden wpis do `androidModule`:
```kotlin
single<NetworkChecker> { AndroidNetworkChecker(androidContext()) }
```

#### 4. Uprawnienie Android

**Plik**: `frontend/androidApp/src/main/AndroidManifest.xml`

**Cel**: Dodać deklarację uprawnienia wymaganego przez `ConnectivityManager`.

**Kontrakt**: Dodać w sekcji `<manifest>` (poza `<application>`):
```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Projekt kompiluje się bez błędów: `./gradlew :androidApp:assembleDebug`
- Brak błędów Koin w logach przy starcie (sprawdź przez ADB: `adb logcat | grep -i koin`)

#### Weryfikacja ręczna:

- Aplikacja startuje poprawnie na urządzeniu/emulatorze

---

## Faza 2: Offline guard w FlashcardsScreen

### Przegląd

Wstrzykuje `NetworkChecker` w `FlashcardsScreen` i blokuje przycisk "▶ Nauka" gdy offline, aktualizuje się reaktywnie przy zmianie łączności.

### Wymagane zmiany:

#### 1. FlashcardsScreen — offline guard

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/FlashcardsScreen.kt`

**Cel**: Przycisk "▶ Nauka" (linia 73-76) wyłączony gdy brak połączenia sieciowego. Reaktywna aktualizacja przez `collectAsState()`.

**Kontrakt**: Dodać parametr `networkChecker: NetworkChecker = koinInject()` do sygnatury `FlashcardsScreen`, zebrać `isOnline` przez `collectAsState()`, rozszerzyć `enabled` o `&& isOnline`:

```kotlin
fun FlashcardsScreen(
    collection: CollectionDto,
    viewModel: FlashcardsViewModel = koinViewModel { parametersOf(collection.id) },
    networkChecker: NetworkChecker = koinInject(),
    onBack: () -> Unit,
    onStartLearning: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by networkChecker.isOnline.collectAsState()
    ...
    TextButton(
        onClick = onStartLearning,
        enabled = uiState.flashcards.isNotEmpty() && isOnline,
    ) { Text("▶ Nauka") }
```

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja bez błędów: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Na urządzeniu/emulatorze: włącz tryb samolotowy → przycisk "▶ Nauka" jest wyłączony (wyszarzony)
- Wyłącz tryb samolotowy → przycisk ponownie aktywny (bez ponownego uruchamiania aplikacji)
- Naciśnięcie "▶ Nauka" online → sesja nauki startuje normalnie
- Uruchom sesję → wyłącz WiFi/dane → sesja kontynuuje odtwarzanie (NFR: offline po starcie)

---

## Strategia testowania

### Testy jednostkowe:

- Brak nowych testów jednostkowych — `NetworkChecker` to interfejs systemowy; testowanie z mock ConnectivityManager wychodzi poza zakres MVP.

### Kroki testowania ręcznego:

1. Uruchom app online, przejdź do FlashcardsScreen z fiszkami — "▶ Nauka" aktywny
2. Włącz tryb samolotowy — "▶ Nauka" wyłącza się reaktywnie (bez restartu)
3. Wyłącz tryb samolotowy — "▶ Nauka" ponownie aktywny
4. Naciśnij "▶ Nauka" online — przejdź do LearningScreen — sesja startuje normalnie
5. W trakcie sesji włącz tryb samolotowy — TTS kontynuuje, sesja nie przerywa się
6. Wróć do FlashcardsScreen (Stop) — "▶ Nauka" wyłączony (nadal w trybie samolotowym)

## Referencje

- Wzorzec: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/di/AndroidModule.kt:9` (LearningController wiring)
- Wzorzec: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningController.kt` (interfejs commonMain)
- Miejsce zmiany: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/FlashcardsScreen.kt:73-76` (enabled condition)
- PRD NFR: "odtwarzanie audio i dostęp do treści fiszek kolekcji nie są uzależnione od łączności sieciowej po uruchomieniu sesji"

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: NetworkChecker — interfejs + implementacja Android + Koin

#### Automatyczne

- [x] 1.1 Kompilacja bez błędów: `./gradlew :androidApp:assembleDebug` — 0f8fb06

#### Ręczne

- [x] 1.2 Aplikacja startuje poprawnie na urządzeniu/emulatorze (brak Koin crash) — 0f8fb06

### Faza 2: Offline guard w FlashcardsScreen

#### Automatyczne

- [x] 2.1 Kompilacja bez błędów: `./gradlew :androidApp:assembleDebug`

#### Ręczne

- [x] 2.2 Tryb samolotowy → przycisk "▶ Nauka" wyłączony (reaktywnie, bez restartu)
- [x] 2.3 Wyłącz tryb samolotowy → przycisk ponownie aktywny
- [x] 2.4 Sesja nauki online → włącz tryb samolotowy w trakcie → sesja nie przerywa się (NFR)
