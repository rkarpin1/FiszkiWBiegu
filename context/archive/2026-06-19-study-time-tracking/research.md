---
date: 2026-06-19T12:00:00Z
researcher: Robert Karpiński
git_commit: 94f38c4eb875e4cd2dbcca056f01c9eb1728cbb9
branch: MVP
repository: FiszkiWBiegu
topic: "Śledzenie łącznego czasu nauki kolekcji"
tags: [research, backend, frontend, collections, learning, study-time]
status: complete
last_updated: 2026-06-19
last_updated_by: Robert Karpiński
---

# Research: Śledzenie łącznego czasu nauki kolekcji

**Date**: 2026-06-19  
**Git Commit**: `94f38c4`  
**Branch**: MVP

## Research Question

Trzeba obsłużyć i zapamiętać w backendzie łączny czas nauki danej kolekcji. Czas ten jest w minutach. Ma on się wyświetlać w oknie kolekcji (jest na to pole). Np. `30 min`, `3 dn 23 min`, `578 dn 78 min`.

## Summary

Infrastruktura do wysyłania danych po sesji jest już gotowa (`learning_complete` endpoint + `LearningViewModel.stop()`). Brakuje:
1. Kolumny `total_study_minutes` w bazie (nowa migracja 010)
2. Pola `session_minutes` w `LearningCompleteRequest` (backend + frontend)
3. Pomiaru czasu aktywnej sesji i przekazania go do `stop()`
4. Formatowania i wyświetlenia w `FlashcardsScreen.kt:268`

Czas aktywnej sesji (`elapsedSec`) istnieje już jako Compose state w `LearningScreen.kt:93` — trzeba go tylko przekazać do `LearningViewModel.stop()`.

---

## Detailed Findings

### Backend — aktualny stan

**Migracje** (`apps/backend/migrations/`):
- Pliki 001–009 istnieją; **następna migracja: 010**
- `collections` nie ma pola czasu nauki. Pola związane z czasem: `created_at` (001), `last_studied` (006)

**Kompletny schemat `collections`:**
| Kolumna | Typ | Skąd |
|---|---|---|
| id | UUID PK | 001 |
| user_id | UUID FK | 001 |
| name | TEXT | 001 |
| created_at | TIMESTAMPTZ DEFAULT now() | 001 |
| source_language | TEXT DEFAULT 'pl' | 003 |
| target_language | TEXT DEFAULT 'en' | 003 |
| description | TEXT DEFAULT '' | 004 |
| last_studied | TIMESTAMPTZ NULL | 006 |
| progress | FLOAT DEFAULT 0 | 006 |

**`Collection` struct** (`apps/backend/src/models.rs:21-33`):
```rust
pub struct Collection {
    pub id: Uuid,
    pub user_id: Uuid,
    pub name: String,
    pub description: String,
    pub source_language: String,
    pub target_language: String,
    pub created_at: DateTime<Utc>,
    pub last_studied: Option<DateTime<Utc>>,
    pub progress: f64,
    pub flashcard_count: i64,  // computed w query, nie w DB
}
```

**`LearningCompleteRequest`** (`apps/backend/src/models.rs:43-46`):
```rust
pub struct LearningCompleteRequest {
    pub progress: f32,
}
```

**Handler `learning_complete`** (`apps/backend/src/handlers/collections.rs:137-164`):
```sql
UPDATE collections SET last_studied = NOW(), progress = $1
WHERE id = $2 AND user_id = $3
```
Nie dotyka czasu nauki — gotowe miejsce na `total_study_minutes = total_study_minutes + $N`.

---

### Frontend — aktualny stan

**`CollectionDto`** (`apps/frontend/shared/src/commonMain/.../data/api/ApiModels.kt:10-35`):
```kotlin
data class CollectionDto(
    val id: String,
    val userId: String,
    val name: String,
    val description: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val createdAt: String,
    val lastStudied: String? = null,
    val progress: Float = 0f,
    val flashcardCount: Int = 0,
    // brak totalStudyMinutes
)
```

**`LearningCompleteRequest`** (`ApiModels.kt:129-132`):
```kotlin
data class LearningCompleteRequest(
    val progress: Float,
    // brak sessionMinutes
)
```

**Timer sesji** (`LearningScreen.kt:93-101`):
```kotlin
var elapsedSec by remember { mutableStateOf(0) }
LaunchedEffect(state.isPlaying) {
    if (state.isPlaying) {
        while (true) {
            delay(1000.milliseconds)
            elapsedSec++
        }
    }
}
```
Liczy **aktywny czas** (zatrzymuje się na pauzie). Wyświetlany w UI jako `MM:SS` (`LearningScreen.kt:141-147`). **Nie jest nigdzie wysyłany.**

**`LearningViewModel.stop()`** (`LearningViewModel.kt:43-52`):
```kotlin
fun stop() {
    val s = controller.state.value
    if (s.flashcards.isNotEmpty()) {
        val progress = s.flashcards.map { it.decayLevel() }.average().toFloat()
        viewModelScope.launch {
            collectionRepo.markStudied(collectionId, progress)
        }
    }
    controller.stop()
}
```
Nie przyjmuje `elapsedSec`. Trzeba dodać parametr.

**StatTile "CZAS"** (`FlashcardsScreen.kt:268`):
```kotlin
StatTile(label = "CZAS", value = "—", modifier = Modifier.weight(1f))
```
Placeholder. Gotowe miejsce do podpięcia `collection.totalStudyMinutes`.

**`LearningService`** (`androidApp/.../LearningService.kt`):
- Brak śledzenia czasu sesji na poziomie serwisu
- `elapsedSec` istnieje tylko w warstwie Compose (`LearningScreen.kt`)
- Serwis ma `globalIndex` i `isPlaying`, ale nie mierzy czasu

---

## Wymagane zmiany

### 1. Migration `010_add_total_study_minutes.sql`
```sql
ALTER TABLE collections
ADD COLUMN total_study_minutes INTEGER NOT NULL DEFAULT 0;
```

### 2. Backend — `models.rs`
```rust
pub struct Collection {
    // ...istniejące pola...
    pub total_study_minutes: i64,
}

pub struct LearningCompleteRequest {
    pub progress: f32,
    pub session_minutes: i32,
}
```

### 3. Backend — `handlers/collections.rs`
`list` query: dodać `c.total_study_minutes` do SELECT.

`learning_complete` SQL:
```sql
UPDATE collections
SET last_studied = NOW(),
    progress = $1,
    total_study_minutes = total_study_minutes + $2
WHERE id = $3 AND user_id = $4
```

### 4. Frontend — `ApiModels.kt`
```kotlin
data class CollectionDto(
    // ...
    @SerialName("total_study_minutes")
    val totalStudyMinutes: Int = 0,
)

data class LearningCompleteRequest(
    val progress: Float,
    @SerialName("session_minutes")
    val sessionMinutes: Int,
)
```

### 5. Frontend — `ApiClient` + `CollectionRepository`
Dodać `sessionMinutes: Int` przez cały stos:
- `ApiClient.patchLearningComplete(collectionId, progress, sessionMinutes)`
- `CollectionRepository.markStudied(id, progress, sessionMinutes)`

### 6. Frontend — `LearningScreen.kt` + `LearningViewModel.kt`

`LearningViewModel.stop()` przyjmuje `elapsedSec: Int`:
```kotlin
fun stop(elapsedSec: Int = 0) {
    val s = controller.state.value
    if (s.flashcards.isNotEmpty()) {
        val progress = s.flashcards.map { it.decayLevel() }.average().toFloat()
        val sessionMinutes = elapsedSec / 60
        viewModelScope.launch {
            collectionRepo.markStudied(collectionId, progress, sessionMinutes)
        }
    }
    controller.stop()
}
```

`LearningScreen.kt` — przekazanie `elapsedSec` do callbacków:
```kotlin
// Capture current value for DisposableEffect
val currentElapsedSec = rememberUpdatedState(elapsedSec)
DisposableEffect(Unit) { onDispose { viewModel.stop(currentElapsedSec.value) } }

// Back button
onBack = { viewModel.stop(elapsedSec); onBack() },
```

### 7. Frontend — `FlashcardsScreen.kt:268`

Pomocnicza funkcja formatowania:
```kotlin
private fun formatStudyTime(minutes: Int): String {
    if (minutes < 1440) return "$minutes min"
    val days = minutes / 1440
    val rem = minutes % 1440
    return "$days dn $rem min"
}
```

Zastąpienie placeholdera:
```kotlin
StatTile(
    label = "CZAS",
    value = formatStudyTime(collection.totalStudyMinutes),
    modifier = Modifier.weight(1f)
)
```

---

## Code References

- `apps/backend/migrations/` — migracje 001–009; następna: 010
- `apps/backend/src/models.rs:21-46` — `Collection` i `LearningCompleteRequest`
- `apps/backend/src/handlers/collections.rs:137-164` — `learning_complete` handler
- `apps/frontend/shared/src/commonMain/.../data/api/ApiModels.kt:10-35, 129-132` — `CollectionDto`, `LearningCompleteRequest`
- `apps/frontend/shared/src/commonMain/.../screens/learning/LearningScreen.kt:93-101` — `elapsedSec` timer
- `apps/frontend/shared/src/commonMain/.../screens/learning/LearningScreen.kt:141-147` — wyświetlanie timera MM:SS
- `apps/frontend/shared/src/commonMain/.../screens/learning/LearningViewModel.kt:43-52` — `stop()`
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/FlashcardsScreen.kt:268` — StatTile "CZAS" z "—"
- `apps/frontend/androidApp/.../LearningService.kt` — brak śledzenia czasu sesji

## Architecture Insights

- `elapsedSec` w `LearningScreen` zlicza **aktywny czas** (zatrzymuje się na pauzie) — to właściwa miara do "czasu nauki"
- `DisposableEffect.onDispose` wymaga `rememberUpdatedState`, żeby uchwycić bieżącą wartość `elapsedSec` przy zamknięciu ekranu
- Backend akumuluje `total_study_minutes += session_minutes` przy każdym `learning_complete` — wartość rośnie historycznie, nigdy nie jest nadpisywana
- `session_minutes = elapsedSec / 60` (integer division) — sesja krótsza niż 60 sekund daje 0 minut (akceptowalne)

## Open Questions

- Czy `total_study_minutes` powinno być resetowane przy usunięciu kolekcji? (nie — DELETE CASCADE wystarczy)
- Czy pokazywać czas nauki na `CollectionsScreen` (hero kolekcji)? Aktualnie spec mówi tylko o `FlashcardsScreen`.
