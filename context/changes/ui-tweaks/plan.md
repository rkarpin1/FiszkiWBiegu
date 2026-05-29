---
change_id: ui-tweaks
title: Poprawki UI
status: planned
created: 2026-05-29
updated: 2026-05-29
---

## Overview

Cztery zmiany UI traktowane jako jedno zadanie:

1. **Usuń menu z listy kolekcji** — MoreVert (Edytuj/Usuń) znikają z `LaneRow` w `CollectionsScreen`; edycja i usuwanie kolekcji dostępne z widoku szczegółów.
2. **Przenieś menu Edycja/Usuń do widoku kolekcji** — MoreVert w top barze `FlashcardsScreen`; `FlashcardItem` również dostaje MoreVert zamiast TextButton.
3. **Dodaj info o kolekcji w liście** — `"10 fiszek · 3 dni temu"` pod nazwą w `LaneRow` (wymaga `flashcard_count` z backendu + formatowania `lastStudied`).
4. **Napraw zasłanianie formularzy przez klawiaturę** — duży nagłówek (`displayMedium`) przeniesiony do obszaru scrollowalnego w `CollectionFormScreen` i `CardFormScreen`.

## What We're NOT Doing

- Zmiana logiki ViewModeli (poza drobnym dodaniem `deleteCollection` do `CollectionsViewModel`)
- Zmiana wyglądu ekranu nauki (`LearningScreen`)
- Zmiana nawigacji poza App.kt

---

## Phase 1: Backend — `flashcard_count` w kolekcji

**Wynik:** `GET /collections` zwraca pole `flashcard_count: i64` — liczba fiszek w kolekcji.

### Changes Required

- **`backend/src/models.rs`** — `Collection` struct: dodaj `flashcard_count: i64`

- **`backend/src/handlers/collections.rs`**
  - `list()`: rozszerz SELECT o subzapytanie COUNT:
    ```sql
    SELECT c.*,
        (SELECT COUNT(*) FROM flashcards WHERE collection_id = c.id) AS flashcard_count
    FROM collections c
    WHERE c.user_id = $1
    ORDER BY c.created_at ASC
    ```
  - `create()` i `update()`: po RETURNING — pobierz `flashcard_count` przez osobne zapytanie COUNT (RETURNING nie obsługuje subzapytań w sqlx) albo zwróć 0 (akceptowalne — po create/update użytkownik widzi kolekcję z nową wartością po odświeżeniu)

### Automated Verification

```bash
cd backend && cargo build 2>&1 | tail -5
```

### Manual Verification

- [ ] `GET /collections` — odpowiedź zawiera pole `flashcard_count` dla każdej kolekcji

---

## Phase 2: Frontend — cztery zmiany UI

**Wynik:** aplikacja z poprawkami: brak menu w liście, menu w szczegółach, info "N fiszek · X dni temu", klawiatura nie zasłania formularzy.

### Changes Required

#### 2.1 ApiModels.kt

Dodaj `@SerialName("flashcard_count") val flashcardCount: Int = 0` do `CollectionDto`.

#### 2.2 CollectionsScreen.kt — usuń menu, dodaj subtitle

- `LaneRow`: usuń parametry `onEditClick` i `onDeleteClick`, usuń `DropdownMenu`/`DropdownMenuItem`/`MoreVert`
- `LaneRow`: dodaj subtitle pod nazwą kolekcji:
  ```kotlin
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
      Text(
          "${collection.flashcardCount} fiszek",
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = mono(), color = accent),
      )
      Text("·", style = ..., color = c.mute2)
      Text(
          formatLastStudied(collection.lastStudied),
          style = MaterialTheme.typography.labelMedium.copy(color = c.mute),
      )
  }
  ```
- `CollectionsScreen`: usuń parametr `onEditClick: (CollectionDto) -> Unit`
- Dodaj pomocnik `formatLastStudied(lastStudied: String?): String` (w `CollectionsScreen.kt` lub `theme/`):
  ```kotlin
  fun formatLastStudied(lastStudied: String?): String {
      lastStudied ?: return "nie ćwiczono"
      return try {
          val instant = Instant.parse(lastStudied)
          val days = (Clock.System.now() - instant).inWholeDays
          when {
              days == 0L -> "dziś"
              days == 1L -> "wczoraj"
              days < 7L -> "$days dni temu"
              days < 30L -> "${days / 7} tyg. temu"
              else -> "${days / 30} mies. temu"
          }
      } catch (_: Exception) { "" }
  }
  ```

#### 2.3 CollectionsViewModel.kt — dodaj `deleteCollection`

```kotlin
fun deleteCollection(id: String) {
    viewModelScope.launch {
        repo.delete(id).fold(
            onSuccess = { loadCollections() },
            onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
        )
    }
}
```

#### 2.4 FlashcardsScreen.kt — MoreVert w top barze i w FlashcardItem

- Dodaj parametry: `onEditCollection: () -> Unit` i `onDeleteCollection: () -> Unit`
- Top bar: zastąp pusty `Spacer(40.dp)` po prawej przyciskiem MoreVert:
  ```kotlin
  var showCollectionMenu by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  Box {
      Icon(Icons.Default.MoreVert, ..., modifier = Modifier.clickable { showCollectionMenu = true })
      DropdownMenu(expanded = showCollectionMenu, ...) {
          DropdownMenuItem(text = { Text("Edytuj") }, onClick = { showCollectionMenu = false; onEditCollection() })
          DropdownMenuItem(text = { Text("Usuń", color = error) }, onClick = { showCollectionMenu = false; showDeleteDialog = true })
      }
  }
  if (showDeleteDialog) {
      AlertDialog(
          title = { Text("Usuń kolekcję") },
          text = { Text("Czy na pewno chcesz usunąć kolekcję \"${collection.name}\"? Tej operacji nie można cofnąć.") },
          confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDeleteCollection() }) { Text("Usuń", color = error) } },
          dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") } },
          onDismissRequest = { showDeleteDialog = false },
      )
  }
  ```
- `FlashcardItem`: zastąp `TextButton("Edytuj") + TextButton("Usuń")` przez MoreVert DropdownMenu:
  ```kotlin
  var showMenu by remember { mutableStateOf(false) }
  Box {
      Icon(Icons.Default.MoreVert, ..., modifier = Modifier.clickable { showMenu = true })
      DropdownMenu(expanded = showMenu, ...) {
          DropdownMenuItem(text = { Text("Edytuj") }, onClick = { showMenu = false; onEditClick() })
          DropdownMenuItem(text = { Text("Usuń", color = error) }, onClick = { showMenu = false; onDeleteClick() })
      }
  }
  ```

#### 2.5 CollectionFormScreen.kt — naprawa klawiatury

Przenieś `Column(Heading "Co dziś do worka?")` i `Spacer(24.dp)` do wnętrza scrollowalnej kolumny z formularzem (bezpośrednio przed polami). Zewnętrzna kolumna (`fillMaxSize`) zawiera tylko: top bar, scrollable area (z nagłówkiem + polami), CTA.

```kotlin
// Przed:
Column(fillMaxSize) {
    Row(/* top bar */)
    Column(/* heading */)       // ← poza scrollem
    Spacer(24dp)
    Column(weight(1f), verticalScroll) { /* pola */ }
    Box(CTA + imePadding)
}

// Po:
Column(fillMaxSize) {
    Row(/* top bar */)
    Column(weight(1f), verticalScroll) {
        Column(/* heading — teraz wewnątrz scrolla */)
        Spacer(24dp)
        /* pola */
    }
    Box(CTA + imePadding)
}
```

#### 2.6 CardFormScreen.kt — naprawa klawiatury

To samo: przenieś nagłówek (duży tytuł ekranu) do wnętrza scrollowalnej kolumny.

#### 2.7 App.kt — usunięcie `onEditClick` i dodanie callbacków do Flashcards

- `Route.Collections` entry: usuń `onEditClick` (przeniesione do `FlashcardsScreen`)
- `Route.Flashcards` entry:
  ```kotlin
  entry<Route.Flashcards> { route ->
      val collectionsVm: CollectionsViewModel = koinViewModel()
      FlashcardsScreen(
          collection = route.collection,
          onBack = { backStack.removeLastOrNull() },
          onStartLearning = { backStack.add(Route.Learning(route.collection)) },
          onAddCard = { backStack.add(Route.CardForm(route.collection.id, route.collection.name)) },
          onEditCard = { flashcard -> backStack.add(Route.CardForm(route.collection.id, route.collection.name, flashcard)) },
          onEditCollection = {
              backStack.add(Route.CollectionForm(
                  route.collection.id, route.collection.name,
                  route.collection.description, route.collection.sourceLanguage, route.collection.targetLanguage,
              ))
          },
          onDeleteCollection = {
              collectionsVm.deleteCollection(route.collection.id)
              backStack.removeLastOrNull()
          },
      )
  }
  ```

### Automated Verification

```bash
cd frontend && ./gradlew :androidApp:assembleDebug 2>&1 | tail -20
```

### Manual Verification

- [ ] Lista kolekcji — brak menu MoreVert przy każdej kolekcji
- [ ] Lista kolekcji — każda kolekcja pokazuje "N fiszek · X dni temu" (lub "nie ćwiczono")
- [ ] Widok kolekcji (FlashcardsScreen) — MoreVert w top barze → "Edytuj" otwiera formularz, "Usuń" pyta o potwierdzenie i wraca do listy
- [ ] Widok kolekcji — każda fiszka ma MoreVert → "Edytuj" / "Usuń" zamiast TextButton
- [ ] CollectionFormScreen — po kliknięciu pola i wysuięciu klawiatury, pole tekstowe jest widoczne i nie zasłonięte nagłówkiem
- [ ] CardFormScreen — j.w.

---

## Progress

### Phase 1: Backend — flashcard_count
#### Automated
- [x] 1.1 models.rs — Collection: dodaj flashcard_count: i64 — 050d846
- [x] 1.2 handlers/collections.rs — list(): COUNT subzapytanie — 050d846
- [x] 1.3 handlers/collections.rs — create/update: flashcard_count = 0 w zwracanym obiekcie — 050d846
- [x] 1.4 cargo build — kompiluje się — 050d846

#### Manual
- [ ] 1.5 GET /collections zwraca flashcard_count

### Phase 2: Frontend — UI tweaks
#### Automated
- [x] 2.1 ApiModels.kt — dodaj flashcardCount do CollectionDto — 293b2c5
- [x] 2.2 CollectionsScreen.kt — usuń DropdownMenu z LaneRow; dodaj subtitle; usuń onEditClick — 293b2c5
- [x] 2.3 CollectionsViewModel.kt — dodaj deleteCollection() — 293b2c5
- [x] 2.4 FlashcardsScreen.kt — MoreVert w top barze + MoreVert w FlashcardItem — 293b2c5
- [x] 2.5 CollectionFormScreen.kt — heading do scrollowalnej kolumny — 293b2c5
- [x] 2.6 CardFormScreen.kt — heading do scrollowalnej kolumny — 293b2c5
- [x] 2.7 App.kt — usuń onEditClick z Collections; dodaj onEditCollection/onDeleteCollection do Flashcards — 293b2c5
- [x] 2.8 ./gradlew :androidApp:assembleDebug — BUILD SUCCESSFUL — 293b2c5

#### Manual
- [ ] 2.9 Lista kolekcji: brak MoreVert, widoczny subtitle "N fiszek · X dni temu"
- [ ] 2.10 FlashcardsScreen: MoreVert kolekcji działa (Edytuj + Usuń z potwierdzeniem)
- [ ] 2.11 FlashcardsScreen: MoreVert fiszek działa (Edytuj + Usuń)
- [ ] 2.12 CollectionFormScreen: klawiatura nie zasłania pól
- [ ] 2.13 CardFormScreen: klawiatura nie zasłania pól
