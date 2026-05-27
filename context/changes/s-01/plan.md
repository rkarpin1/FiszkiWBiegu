# Zarządzanie kolekcjami i fiszkami — Plan implementacji E2E

## Przegląd

S-01 uzupełnia ostatnie braki UI w przepływie CRUD kolekcji i fiszek, następnie weryfikuje cały flow E2E na fizycznym urządzeniu Android. Backend, data layer i większość UI jest gotowa — zostały 3 luki: brak rename kolekcji w UI, brak przycisku logout, brak przycisku Retry przy błędzie sieciowym.

## Analiza stanu obecnego

**Gotowe:**
- `CollectionsScreen` — Create, Read, Delete + `DeleteConfirmationDialog`
- `FlashcardsScreen` — pełny CRUD (Create, Read, Update, Delete) + dialogi
- `App.kt:30` — auto-login: `authRepository.isLoggedIn()` → skip do Collections
- Backend — wszystkie 10 endpointów obecnych i autoryzowanych
- Data layer — `CollectionRepository.rename(id, name)` i `PUT /collections/{id}` gotowe

**Braki:**
- `CollectionsScreen` / `CollectionsViewModel` — brak UI i logiki rename (FR-004)
- `CollectionsScreen` — brak przycisku logout; `App.kt:57` nie przekazuje `onLogout`
- Oba ekrany — brak przycisku Retry przy `uiState.error != null`

## Pożądany stan końcowy

Użytkownik na fizycznym urządzeniu Android może: zalogować się przez Google, tworzyć/edytować/usuwać kolekcje i fiszki (z dialogiem potwierdzenia przed każdym usunięciem), wylogować się i wrócić do LoginScreen, a przy błędzie sieciowym — ponowić operację jednym kliknięciem.

### Kluczowe odkrycia:

- `CollectionsViewModel.kt` przyjmuje `CollectionRepository` który ma `rename()` — żadnych zmian DI
- `App.kt:57` — `CollectionsScreen(onCollectionClick = ...)` wymaga dodania parametru `onLogout`; `authRepository` jest już wstrzyknięty w `App.kt:28`
- `CollectionsScreen` nie ma `TopAppBar` — zostanie dodany wraz z ikoną logout
- `webClientId` zahardkodowany w `MainActivity.kt:10` — tech debt, nie zmieniamy w tym planie

## Czego NIE robimy

- Refaktoru `webClientId` do `strings.xml` — odkładamy
- Pozycjonowania / drag-drop fiszek
- Testów automatycznych (unit/integration)
- Obsługi offline / cache (to F-01)
- Wylogowania po stronie serwera (token revocation)

## Podejście do implementacji

Trzy małe zmiany UI (rename, logout, retry) w Fazie 1 — każda niezależna, można commitować osobno. Faza 2 to build + ręczny test E2E na urządzeniu z checklistą — każdy punkt checklisty musi przejść, żeby S-01 był ukończony.

---

## Faza 1: Uzupełnienie UI

### Przegląd

Dodanie trzech brakujących elementów UI: rename kolekcji, logout, Retry przy błędzie.

### Wymagane zmiany:

#### 1. Rename kolekcji — ViewModel

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsViewModel.kt`

**Cel**: Dodać obsługę stanu i logikę edycji nazwy kolekcji.

**Kontrakt**:
- Rozszerzyć `CollectionsUiState` o: `editingCollectionId: String? = null`, `editingCollectionName: String = ""`
- Dodać metody:
  - `requestEdit(id: String, currentName: String)` — ustawia `editingCollectionId` i `editingCollectionName`
  - `cancelEdit()` — czyści pola edycji
  - `confirmEdit(newName: String)` — wywołuje `collectionRepository.rename(editingCollectionId!!, newName)`, po sukcesie wywołuje `loadCollections()`, w razie błędu ustawia `uiState.error`

#### 2. Rename kolekcji — UI

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Dodać przycisk edycji w wierszu kolekcji i dialog zmiany nazwy.

**Kontrakt**:
- `CollectionItem` — obok ikony usuń dodać `IconButton` z `Icons.Default.Edit`; kliknięcie wywołuje `viewModel.requestEdit(collection.id, collection.name)`. **Uwaga**: `shared/build.gradle.kts` nie zawiera zależności icons (`material-icons-core`). Jeśli `Icon(Icons.Default.Edit)` nie kompiluje się, dodaj do `commonMain.dependencies`: `implementation("org.jetbrains.compose.material:material-icons-core:1.11.0")` lub zamień na `TextButton("Edytuj")` wzorem `FlashcardItem`
- Nowy composable `EditCollectionDialog` — analogiczny do istniejącego `AddCollectionDialog` (TextField pre-filled z `uiState.editingCollectionName`); przyciski Anuluj / Zapisz
- Wyświetlać `EditCollectionDialog` gdy `uiState.editingCollectionId != null`

#### 3. Logout — App.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Przekazać callback `onLogout` do `CollectionsScreen`.

**Kontrakt**: W bloku `Destination.Collections` (linia 57) dodać parametr:
```
onLogout = {
    authRepository.logout()
    destination = Destination.Login
}
```

#### 4. Logout — CollectionsScreen

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Dodać `TopAppBar` z tytułem "Moje kolekcje" i ikoną logout.

**Kontrakt**:
- Dodać parametr `onLogout: () -> Unit` do `CollectionsScreen()`
- Dodać parametr `topBar` do istniejącego `Scaffold`: `topBar = { CenterAlignedTopAppBar(title = { Text("Moje kolekcje") }, actions = { IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, contentDescription = "Wyloguj") } }) }` — `Icons.Default.ExitToApp` jest w podstawowym zestawie Material3, nie wymaga zależności extended

#### 5. Retry — CollectionsScreen

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Pokazać przycisk "Spróbuj ponownie" gdy `uiState.error != null`.

**Kontrakt**: Oba ekrany używają standalone `Snackbar` composable renderowanego bezpośrednio w `Box` (bez `SnackbarHostState`). Rozszerzyć istniejący blok:
```kotlin
uiState.error?.let {
    Snackbar(
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        action = {
            TextButton(onClick = { viewModel.loadCollections() }) { Text("Ponów") }
        }
    ) { Text(it) }
}
```

#### 6. Retry — FlashcardsScreen

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/FlashcardsScreen.kt`

**Cel**: Analogicznie do CollectionsScreen — Retry przy błędzie.

**Kontrakt**: Analogicznie do CollectionsScreen — rozszerzyć istniejący blok `uiState.error?.let { Snackbar(...) }` o parametr `action`:
```kotlin
action = {
    TextButton(onClick = { viewModel.loadFlashcards() }) { Text("Ponów") }
}
```

---

### Kryteria sukcesu Fazy 1:

#### Weryfikacja automatyczna:

- Build APK bez błędów: `./gradlew :androidApp:assembleDebug` (z katalogu `frontend/`)

#### Weryfikacja ręczna:

- Rename kolekcji: kliknięcie ikony ołówka → dialog z aktualną nazwą → zmiana → kolekcja na liście pokazuje nową nazwę
- Logout: kliknięcie ikony logout → ekran logowania
- Retry: symulacja błędu (wyłączenie sieci) → widoczny przycisk "Spróbuj ponownie" → po kliknięciu próba ponownego pobrania

**Uwaga implementacyjna**: Po zakończeniu Fazy 1 i przejściu automatycznej weryfikacji — zatrzymaj się na ręczne potwierdzenie powyższych 3 punktów przed przejściem do Fazy 2.

---

## Faza 2: Build i weryfikacja E2E na urządzeniu

### Przegląd

Pełne przejście przepływu E2E na fizycznym urządzeniu Android po ukończeniu Fazy 1.

### Wymagane kroki:

#### 1. Build i instalacja

**Cel**: Zbudować debug APK i zainstalować na urządzeniu.

**Kontrakt**:
```bash
# z katalogu frontend/
./gradlew :androidApp:assembleDebug
# APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

#### 2. Weryfikacja E2E — checklista

**Cel**: Przejść pełny przepływ i potwierdzić brak błędów.

**Kontrakt** — wszystkie punkty muszą przejść:

| # | Akcja | Oczekiwany wynik |
|---|-------|-----------------|
| 1 | Uruchom aplikację z wyczyszczonymi danymi | LoginScreen |
| 2 | Kliknij "Zaloguj się przez Google" | Picker konta Google → zalogowany → CollectionsScreen |
| 3 | Zamknij i wznów aplikację | Bezpośrednio CollectionsScreen (auto-login) |
| 4 | Utwórz kolekcję "Angielski podstawowy" | Pojawia się na liście |
| 5 | Edytuj nazwę kolekcji → "Angielski A1" | Nazwa zmieniona na liście |
| 6 | Kliknij kolekcję → utwórz 2 fiszki (PL+EN) | Fiszki widoczne |
| 7 | Edytuj fiszkę | Wartości zaktualizowane |
| 8 | Usuń fiszkę z dialogiem potwierdzenia | Fiszka znikła; anulowanie nie usuwa |
| 9 | Wróć → usuń kolekcję z dialogiem | Kolekcja znikła; anulowanie nie usuwa |
| 10 | Logout | LoginScreen |

---

### Kryteria sukcesu Fazy 2:

#### Weryfikacja automatyczna:

- Build: `./gradlew :androidApp:assembleDebug` kończy się bez błędów

#### Weryfikacja ręczna:

- Wszystkie 10 punktów checklisty zakończone sukcesem na fizycznym urządzeniu Android

---

## Strategia testowania

### Weryfikacja automatyczna:

- Build: `./gradlew :androidApp:assembleDebug` — sprawdza kompilację Kotlin i KMP

### Kroki testowania ręcznego:

Checklista E2E z Fazy 2 (10 punktów) na fizycznym urządzeniu z Androidem ≥ 10 (minSdk = 30).

## Uwagi dotyczące migracji

Brak — zmiany wyłącznie frontendowe, bez zmian schematu DB ani endpointów API.

## Referencje

- Roadmap: `context/foundation/roadmap.md` (S-01, linie 76-86)
- CollectionsScreen: `frontend/shared/src/commonMain/.../CollectionsScreen.kt`
- CollectionsViewModel: `frontend/shared/src/commonMain/.../CollectionsViewModel.kt`
- App.kt: `frontend/shared/src/commonMain/.../App.kt` (routing, linie 27-71)
- Backend routes: `backend/src/main.rs` (linie 45-73)
- Tech debt — webClientId: `frontend/androidApp/.../MainActivity.kt:10`

---

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Uzupełnienie UI

#### Automatyczne

- [x] 1.1 Build APK bez błędów: `./gradlew :androidApp:assembleDebug` — 3b67f94

#### Ręczne

- [x] 1.2 Rename kolekcji działa (dialog pre-filled, zmiana widoczna na liście) — 3b67f94
- [x] 1.3 Logout przechodzi do LoginScreen — 3b67f94
- [x] 1.4 Przycisk Retry widoczny przy błędzie, ponawia żądanie — 3b67f94

### Faza 2: Build i weryfikacja E2E na urządzeniu

#### Automatyczne

- [x] 2.1 Build APK: `./gradlew :androidApp:assembleDebug` — 89a65c4

#### Ręczne

- [x] 2.2 Aplikacja uruchomiona ze wyczyszczonymi danymi → LoginScreen — 89a65c4
- [x] 2.3 LoginScreen → Google Sign-In → CollectionsScreen — 89a65c4
- [x] 2.4 Auto-login po ponownym uruchomieniu — 89a65c4
- [x] 2.5 Create kolekcji — 89a65c4
- [x] 2.6 Edit (rename) kolekcji — 89a65c4
- [x] 2.7 Create 2 fiszek w kolekcji — 89a65c4
- [x] 2.8 Edit fiszki — 89a65c4
- [x] 2.9 Delete fiszki z potwierdzeniem (anulowanie nie usuwa) — 89a65c4
- [x] 2.10 Delete kolekcji z potwierdzeniem (anulowanie nie usuwa) — 89a65c4
- [x] 2.11 Logout → LoginScreen — 89a65c4
