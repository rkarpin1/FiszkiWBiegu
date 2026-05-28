# UI Reskin — Faza C: Nowe Ekrany i Kompletna Nawigacja

## Przegląd

Zastąpienie trzech `AlertDialog`-ów (`AddCollectionDialog`, `EditCollectionDialog`, `FlashcardFormDialog`) pełnoekranowymi formularzami w stylu Dawn Run oraz aktualizacja nawigacji w `App.kt`. Profile screen był już zrealizowany w S-04-B. Logika repozytoriów i warstwy danych — bez zmian.

## Analiza stanu obecnego

- `CollectionsScreen.kt` — ma inline: `AddCollectionDialog`, `EditCollectionDialog`, `DeleteConfirmationDialog`; VM kontroluje je przez flagi `showAddDialog`, `editingCollectionId`, `pendingDeleteId`
- `FlashcardsScreen.kt` — ma inline: `FlashcardFormDialog`, `DeleteFlashcardDialog`; VM: `showFormDialog`, `editingFlashcard`, `pendingDeleteId`
- `App.kt` — 5 tras: Login, Collections, Flashcards, Learning, Profile; formularz kolekcji i fiszki wciąż przez dialogi
- `CollectionsViewModel` — brak metod do bezpośredniego update/delete bez dance'u flag dialogu
- `FlashcardsViewModel` — brak metod do create/update/delete bez dance'u flag dialogu
- Obie VM zarejestrowane w Koin jako `viewModel { }` — `koinViewModel<T>()` z obu ekranów zwraca tę samą instancję (Activity ViewModelStore), więc lista odświeży się po powrocie z formularza

## Pożądany stan końcowy

1. `CollectionFormScreen.kt` — pełnoekranowy formularz; tryb add (nowa kolekcja) i edit (edycja + usuwanie przez ModalBottomSheet); jasny motyw (`naturalDark = false`)
2. `CardFormScreen.kt` — pełnoekranowy formularz; tryb add i edit; przycisk "Przetłumacz" = disabled; jasny motyw
3. `App.kt` — 7 tras: +`CollectionForm`, +`CardForm`; FAB i "Edytuj" w listach nawigują do formularzy zamiast otwierać dialogi
4. `CollectionsScreen.kt` — usunięte `AddCollectionDialog`, `EditCollectionDialog`; zostaje `DeleteConfirmationDialog` (szybkie usuwanie z listy)
5. `FlashcardsScreen.kt` — usunięty `FlashcardFormDialog`; zostaje `DeleteFlashcardDialog`
6. `./gradlew :androidApp:assembleDebug` bez błędów
7. Wszystkie destrukcyjne akcje mają potwierdzenie (PRD hard rule)

## Kluczowe odkrycia

- **Koin VM sharing**: `koinViewModel<CollectionsViewModel>()` w formularzu zwraca tę samą instancję co w `CollectionsScreen` — po `createCollection()` / `updateCollection()` lista odświeży się automatycznie gdy `loadCollections()` zakończy asynchronicznie
- **Brak bezpośrednich metod VM**: `confirmEdit` wymaga wcześniejszego `requestEdit` (state dance dla dialogu); lepiej dodać `updateCollection(id, name)` i `deleteCollection(id)` do VM; podobnie dla `FlashcardsViewModel`
- **`pendingDeleteId` interference**: wywołanie `requestDelete` z formularza ustawiłoby flagę w VM i `CollectionsScreen` (wciąż w backStack, renderowany) pokazałby `DeleteConfirmationDialog` — dlatego delete z formularza idzie przez nowe metody VM bez flag dialogu
- **ModalBottomSheet**: dostępny w `androidx.compose.material3` (wersja 1.11.0-alpha07); użyty w `CollectionFormScreen` dla potwierdzenia usunięcia kolekcji
- **Pole Opis**: widoczny stub — `OutlinedTextField` wyświetlany ale wartość nie jest persystowana (brak `description` w `CollectionDto`)
- **Translate button**: `Button(enabled = false)` bez dodatkowego feedbacku
- **Delete z listy**: zachowane — `DeleteConfirmationDialog` w `CollectionsScreen` i `DeleteFlashcardDialog` w `FlashcardsScreen` zostają; formularz dodaje drugą drogę do usunięcia
- **Motyw formularzy**: `FiszkiThemedScreen(naturalDark = true)` — ciemny, spójny z pozostałymi ekranami aplikacji

## Czego NIE robimy

- Nie implementujemy `LangSelect` (wybór języka kolekcji) — brak pola `language` w modelu
- Nie persystujemy pola `description` — brak w `CollectionDto`
- Nie implementujemy `TranslateService` — disabled button, S-04-D
- Nie usuwamy istniejących metod `showAddDialog` / `showEditDialog` z VM — nie są w zakresie cleanup'u tej fazy

---

## Phase 1: CollectionFormScreen + CollectionsViewModel

### Changes Required

- Nowy plik `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionFormScreen.kt`
- Modyfikacja `CollectionsViewModel.kt` — dodanie dwóch metod

**CollectionsViewModel — nowe metody:**

```kotlin
fun updateCollection(id: String, name: String) {
    viewModelScope.launch {
        repo.rename(id, name).fold(
            onSuccess = { loadCollections() },
            onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
        )
    }
}

fun deleteCollection(id: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        repo.delete(id).fold(
            onSuccess = { _uiState.update { it.copy(isLoading = false) } },
            onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
        )
    }
}
```

Uwaga: `deleteCollection` w VM nie wywołuje `loadCollections()` bo po delete formularz wraca do `CollectionsScreen`, która sama odświeży listę jeśli trzeba. Można też wywołać `loadCollections()` po `onSuccess` — do wyboru.

**CollectionFormScreen — sygnatura i layout:**

```kotlin
@Composable
fun CollectionFormScreen(
    collectionId: String? = null,
    collectionName: String = "",
    viewModel: CollectionsViewModel = koinViewModel(),
    onBack: () -> Unit,
)
```

- `FiszkiThemedScreen(naturalDark = true)` — ciemny motyw
- State: `var name by remember { mutableStateOf(collectionName) }`, `var description by remember { mutableStateOf("") }`, `var showDeleteSheet by remember { mutableStateOf(false) }`
- `val isEdit = collectionId != null`

**Top bar (Row, horizontal=22dp, vertical=16dp):**
- IconButton (40dp, rounded12, surface2 border): `Icon(if (isEdit) Icons.Default.ArrowBack else Icons.Default.Close)` → `onBack()`
- `Spacer(Modifier.weight(1f))`
- `Text(if (isEdit) "Edytuj kolekcję" else "Nowa kolekcja", style = titleMedium, color = c.text)` (wyśrodkowany)
- `Spacer(Modifier.weight(1f))`
- Jeśli `isEdit`: IconButton z `Icon(Icons.Default.Delete, tint = c.accent)` → `showDeleteSheet = true`; else `Spacer(Modifier.width(40.dp))`

**Heading (padding horizontal=26dp):**
- `Text(if (isEdit) "Co\nzmieniamy?" else "Co dziś\ndo worka?", style = displayMedium, color = c.text)`

**Formularz (Column, fillMaxWidth, padding=26dp, verticalScroll):**
- Sekcja "NAZWA":
  ```kotlin
  CapsLabel("NAZWA")
  Spacer(Modifier.height(6.dp))
  OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
  )
  ```
- Spacer(height=16dp)
- Sekcja "OPIS" (stub):
  ```kotlin
  CapsLabel("OPIS")
  Spacer(Modifier.height(6.dp))
  OutlinedTextField(
      value = description,
      onValueChange = { description = it },
      minLines = 3,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text("Krótka podpowiedź, np. tematyka albo poziom.", color = c.mute) },
  )
  ```

**Sticky CTA (Box, padding=22dp, imePadding):**
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(if (name.isNotBlank()) c.accent else c.surface3)
        .then(if (name.isNotBlank()) Modifier.clickable {
            if (isEdit) { viewModel.updateCollection(collectionId!!, name.trim()); onBack() }
            else { viewModel.createCollection(name.trim()); onBack() }
        } else Modifier),
    contentAlignment = Alignment.Center,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Check, null, tint = if (name.isNotBlank()) c.onAccent else c.mute2)
        Text(
            if (isEdit) "Zapisz zmiany" else "Dodaj kolekcję",
            style = titleMedium,
            color = if (name.isNotBlank()) c.onAccent else c.mute2,
        )
    }
}
```

**ModalBottomSheet (delete confirm, tylko isEdit):**
```kotlin
if (showDeleteSheet) {
    ModalBottomSheet(onDismissRequest = { showDeleteSheet = false }) {
        Column(Modifier.padding(22.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, null, tint = c.accent)
            }
            Spacer(Modifier.height(12.dp))
            Text("Usunąć kolekcję?", style = MaterialTheme.typography.headlineMedium, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "\"$collectionName\" zostanie trwale usunięta. Tej akcji nie można cofnąć.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.mute,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showDeleteSheet = false }, modifier = Modifier.weight(1f)) {
                    Text("Anuluj")
                }
                Button(
                    onClick = {
                        showDeleteSheet = false
                        viewModel.deleteCollection(collectionId!!)
                        onBack()
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) {
                    Text("Usuń kolekcję", color = c.onAccent)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

### Success Criteria

#### Automated Verification

- `./gradlew :shared:compileDebugKotlinAndroid` bez błędów po dodaniu pliku i zmianach w VM

#### Manual Verification

- CollectionFormScreen kompiluje się poprawnie (faza 3 podepnie nawigację — pełna weryfikacja UI wtedy)

---

## Phase 2: CardFormScreen + FlashcardsViewModel

### Changes Required

- Nowy plik `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CardFormScreen.kt`
- Modyfikacja `FlashcardsViewModel.kt` — dodanie trzech metod

**FlashcardsViewModel — nowe metody:**

```kotlin
fun createCard(polishText: String, englishText: String) {
    viewModelScope.launch {
        repo.create(collectionId, polishText, englishText).fold(
            onSuccess = { loadFlashcards() },
            onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
        )
    }
}

fun updateCard(id: String, polishText: String, englishText: String) {
    viewModelScope.launch {
        repo.update(id, polishText, englishText).fold(
            onSuccess = { loadFlashcards() },
            onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
        )
    }
}

fun deleteFlashcard(id: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        repo.delete(id).fold(
            onSuccess = { _uiState.update { it.copy(isLoading = false) } },
            onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
        )
    }
}
```

**CardFormScreen — sygnatura:**

```kotlin
@Composable
fun CardFormScreen(
    collectionId: String,
    collectionName: String,
    flashcard: FlashcardDto? = null,
    viewModel: FlashcardsViewModel = koinViewModel { parametersOf(collectionId) },
    onBack: () -> Unit,
)
```

- `FiszkiThemedScreen(naturalDark = true)` — ciemny motyw
- State: `var polishText by remember { mutableStateOf(flashcard?.polishText ?: "") }`, `var englishText by remember { mutableStateOf(flashcard?.englishText ?: "") }`, `var showDeleteDialog by remember { mutableStateOf(false) }`
- `val isEdit = flashcard != null`
- `val isValid = polishText.isNotBlank() && englishText.isNotBlank()`

**Top bar:** identyczny wzorzec co CollectionFormScreen — Close/ArrowBack + title + trash (edit only)

**Collection plaque (padding horizontal=26dp):**
```kotlin
Box(
    Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(c.surface2)
        .border(1.dp, c.line, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp),
) {
    Text(collectionName, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.text)
}
```

**Heading:**
- `Text(if (isEdit) "Zmień co\nchcesz." else "Para słów.\nPolski i angielski.", style = headlineLarge, color = c.text)`

**Pole PL (z Flag):**
```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Flag("pl", 22.dp)
    Spacer(Modifier.width(8.dp))
    CapsLabel("POLSKI")
}
OutlinedTextField(
    value = polishText,
    onValueChange = { polishText = it },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)
```

**Przycisk Przetłumacz (między polami):**
```kotlin
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(1.dp).background(c.line))
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(19.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Translate, null, tint = c.mute2, modifier = Modifier.size(16.dp))
            Text("Przetłumacz", style = MaterialTheme.typography.labelMedium, color = c.mute2)
        }
    }
    Box(Modifier.weight(1f).height(1.dp).background(c.line))
}
```

Uwaga: brak `onClick`, brak `clickable` — button disabled wizualnie (wyszarzone).

**Pole EN (z Flag):**
```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Flag("en", 22.dp)
    Spacer(Modifier.width(8.dp))
    CapsLabel("ANGIELSKI")
}
OutlinedTextField(
    value = englishText,
    onValueChange = { englishText = it },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)
```

**Sticky CTA (identyczny wzorzec co CollectionFormScreen):**
- "Zapisz zmiany" (edit) / "Dodaj fiszkę" (new)
- Disabled gdy `!isValid`
- On click:
  ```kotlin
  if (isEdit) { viewModel.updateCard(flashcard!!.id, polishText.trim(), englishText.trim()); onBack() }
  else { viewModel.createCard(polishText.trim(), englishText.trim()); onBack() }
  ```

**AlertDialog dla delete (edit only):**
```kotlin
if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Usuń fiszkę") },
        text = { Text("Czy na pewno chcesz usunąć fiszkę \"${flashcard!!.polishText}\"? Tej operacji nie można cofnąć.") },
        confirmButton = {
            TextButton(onClick = {
                showDeleteDialog = false
                viewModel.deleteFlashcard(flashcard!!.id)
                onBack()
            }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
        },
    )
}
```

### Success Criteria

#### Automated Verification

- `./gradlew :shared:compileDebugKotlinAndroid` bez błędów po dodaniu pliku i zmianach w VM

#### Manual Verification

- CardFormScreen kompiluje się poprawnie (pełna weryfikacja UI po Phase 3)

---

## Phase 3: Nawigacja — App.kt + CollectionsScreen + FlashcardsScreen

### Changes Required

**App.kt — nowe trasy:**

Dodać do `sealed interface Route`:
```kotlin
data class CollectionForm(
    val collectionId: String? = null,
    val collectionName: String = "",
) : Route

data class CardForm(
    val collectionId: String,
    val collectionName: String,
    val flashcard: FlashcardDto? = null,
) : Route
```

Dodać wpisy w `entryProvider`:
```kotlin
entry<Route.CollectionForm> { route ->
    CollectionFormScreen(
        collectionId = route.collectionId,
        collectionName = route.collectionName,
        onBack = { backStack.removeLastOrNull() },
    )
}
entry<Route.CardForm> { route ->
    CardFormScreen(
        collectionId = route.collectionId,
        collectionName = route.collectionName,
        flashcard = route.flashcard,
        onBack = { backStack.removeLastOrNull() },
    )
}
```

**CollectionsScreen.kt:**

Dodać parametry:
```kotlin
fun CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
    onAddClick: () -> Unit,           // ← nowe
    onEditClick: (String, String) -> Unit,  // ← nowe (id, name)
)
```

Zmiany:
- Usunąć blok `if (uiState.showAddDialog) { AddCollectionDialog(...) }` 
- Usunąć blok `uiState.editingCollectionId?.let { EditCollectionDialog(...) }`
- Usunąć prywatne composable `AddCollectionDialog` i `EditCollectionDialog`
- FAB `clickable`: `viewModel.showAddDialog()` → `onAddClick()`
- LaneRow `onEditClick`: `viewModel.requestEdit(collection.id, collection.name)` → `onEditClick(collection.id, collection.name)`
- Blok `uiState.pendingDeleteId?.let { ... }` i `DeleteConfirmationDialog` — zostają bez zmian

Wywołanie CollectionsScreen w App.kt:
```kotlin
entry<Route.Collections> {
    CollectionsScreen(
        onCollectionClick = { backStack.add(Route.Flashcards(it)) },
        onAddClick = { backStack.add(Route.CollectionForm()) },
        onEditClick = { id, name -> backStack.add(Route.CollectionForm(id, name)) },
    )
}
```

**FlashcardsScreen.kt:**

Dodać parametry:
```kotlin
fun FlashcardsScreen(
    collection: CollectionDto,
    viewModel: FlashcardsViewModel = koinViewModel { parametersOf(collection.id) },
    networkChecker: NetworkChecker = koinInject(),
    onBack: () -> Unit,
    onStartLearning: () -> Unit,
    onAddCard: () -> Unit,           // ← nowe
    onEditCard: (FlashcardDto) -> Unit,  // ← nowe
)
```

Zmiany:
- Usunąć blok `if (uiState.showFormDialog) { FlashcardFormDialog(...) }`
- Usunąć prywatne composable `FlashcardFormDialog`
- FAB `clickable`: `viewModel.showAddDialog()` → `onAddCard()`
- FlashcardItem `onEditClick`: `viewModel.showEditDialog(flashcard)` → `onEditCard(flashcard)`
- Blok `uiState.pendingDeleteId?.let { ... }` i `DeleteFlashcardDialog` — zostają bez zmian

Wywołanie FlashcardsScreen w App.kt:
```kotlin
entry<Route.Flashcards> { route ->
    FlashcardsScreen(
        collection = route.collection,
        onBack = { backStack.removeLastOrNull() },
        onStartLearning = { backStack.add(Route.Learning(route.collection)) },
        onAddCard = { backStack.add(Route.CardForm(route.collection.id, route.collection.name)) },
        onEditCard = { flashcard -> backStack.add(Route.CardForm(route.collection.id, route.collection.name, flashcard)) },
    )
}
```

### Success Criteria

#### Automated Verification

- `./gradlew :androidApp:assembleDebug` kończy się bez błędów

#### Manual Verification

- Kliknięcie FAB w CollectionsScreen otwiera CollectionFormScreen (tryb "Nowa kolekcja")
- Kliknięcie "Edytuj" w menu kolekcji otwiera CollectionFormScreen (tryb "Edytuj", pola wypełnione)
- Zapisanie nowej kolekcji wraca do listy i kolekcja pojawia się po odświeżeniu
- Zapisanie edycji kolekcji wraca do listy z nową nazwą
- Usunięcie kolekcji z formularza (ModalBottomSheet) wraca do listy bez tej kolekcji
- Usunięcie kolekcji z menu (DeleteConfirmationDialog) nadal działa
- Kliknięcie FAB w FlashcardsScreen otwiera CardFormScreen (tryb "Nowa fiszka")
- Kliknięcie "Edytuj" na fiszce otwiera CardFormScreen (tryb "Edytuj", pola wypełnione)
- Zapisanie fiszki wraca do listy FlashcardsScreen z nową/zaktualizowaną fiszką
- Usunięcie fiszki z formularza (AlertDialog) wraca do listy bez tej fiszki
- Usunięcie fiszki z listy (DeleteFlashcardDialog) nadal działa
- Przycisk "Przetłumacz" widoczny, wyszarzony, nie robi nic po kliknięciu/tapnięciu
- Pełna nawigacja E2E (login → kolekcje → fiszki → nauka) działa bez regresji

---

## Progress

### Phase 1: CollectionFormScreen + CollectionsViewModel

#### Automated
- [x] 1.1 :shared:compileDebugKotlinAndroid po dodaniu CollectionFormScreen.kt i metod VM — 376aa0e

#### Manual
- [x] 1.2 CollectionFormScreen.kt kompiluje się bez błędów IDE (weryfikacja po Phase 3) — 376aa0e

### Phase 2: CardFormScreen + FlashcardsViewModel

#### Automated
- [x] 2.1 :shared:compileDebugKotlinAndroid po dodaniu CardFormScreen.kt i metod VM — a6112e0

#### Manual
- [x] 2.2 CardFormScreen.kt kompiluje się bez błędów IDE (weryfikacja po Phase 3) — a6112e0

### Phase 3: Nawigacja — App.kt + CollectionsScreen + FlashcardsScreen

#### Automated
- [x] 3.1 :androidApp:assembleDebug bez błędów

#### Manual
- [x] 3.2 CollectionFormScreen add — FAB otwiera formularz, zapisanie tworzy kolekcję
- [x] 3.3 CollectionFormScreen edit — "Edytuj" otwiera formularz z danymi, zapisanie edytuje
- [x] 3.4 CollectionFormScreen delete — ModalBottomSheet potwierdza usunięcie kolekcji
- [x] 3.5 Delete z listy (DeleteConfirmationDialog) nadal działa
- [x] 3.6 CardFormScreen add — FAB otwiera formularz, zapisanie tworzy fiszkę
- [x] 3.7 CardFormScreen edit — "Edytuj" otwiera formularz z danymi, zapisanie edytuje
- [x] 3.8 CardFormScreen delete — AlertDialog potwierdza usunięcie fiszki
- [x] 3.9 Delete fiszki z listy nadal działa
- [x] 3.10 Przycisk Przetłumacz disabled, brak feedbacku
- [x] 3.11 Nawigacja E2E bez regresji
