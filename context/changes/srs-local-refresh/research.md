---
date: 2026-06-19T00:00:00+02:00
researcher: Robert Karpiński
git_commit: e57f326dc7596e2a9cca0ebaff6b5927ebdc9a4e
branch: MVP
repository: FiszkiWBiegu
topic: "Odświeżenie lokalnych danych SRS (srsLevel + lastStudiedAt) po ocenie fiszki"
tags: [research, srs, learning, LearningService, FlashcardDto, SrsCard]
status: complete
last_updated: 2026-06-19
last_updated_by: Robert Karpiński
---

# Research: Odświeżenie lokalnych danych SRS po ocenie fiszki

**Date**: 2026-06-19  
**Git Commit**: e57f326dc7596e2a9cca0ebaff6b5927ebdc9a4e  
**Branch**: MVP

## Research Question

Aplikacja wysyła do backendu `srsLevel` po odegraniu fiszki. Chcę, aby ten srs i data zostały odświeżone też w fiszce lokalnej i fiszce reprezentującej kolejkę do nauki.

## Summary

**Problem jest w jednym miejscu**: `applyRating()` w `LearningService.kt` aktualizuje `SrsCard.srsLevel` (pole `var`) i wysyła dane do backendu, ale **nie aktualizuje `SrsCard.flashcard`** (`val FlashcardDto`). Ponieważ `FlashcardDto` jest niemutowalny (data class), pola `srsLevel` i `lastStudiedAt` wewnątrz fiszki pozostają stale aż do przeładowania sesji.

**Skutki:** `SrsLevelIndicator` w `LearningScreen` wywołuje `card.decayLevel()` na starym `FlashcardDto`, `LearningState.flashcards` (mapa `srsQueue`) zawiera fiszki ze starymi wartościami, a `currentCard` też jest stale.

**Naprawa: 2 pliki, minimalna zmiana:**
1. `SrsEngine.kt` — zmień `val flashcard` na `var flashcard` w `SrsCard`
2. `LearningService.kt` — w `applyRating()` dodaj `card.flashcard = card.flashcard.copy(srsLevel = newLevel, lastStudiedAt = now.toString())`

## Detailed Findings

### Przepływ od kliknięcia "Wiem!" do backendu

1. `LearningScreen.kt:75` — `onKnowWell = { viewModel.rate(Rating.KNOW_WELL) }`
2. `LearningViewModel.kt` — `fun rate(rating: Rating) = controller.rate(rating)`
3. `AndroidLearningController.kt` — wysyła `Intent(ACTION_RATE)` do `LearningService`
4. `LearningService.kt:238–248` — obsługuje `ACTION_RATE`, wywołuje `applyRating(card, rating)`
5. `LearningService.kt:343–349` — **`applyRating()`** — tu jest problem

### applyRating() — aktualny stan (problem)

```kotlin
// LearningService.kt:343-349
private fun applyRating(card: SrsCard, rating: Rating) {
    card.srsLevel = SrsEngine.newLevel(card.srsLevel, rating)   // ✅ SrsCard.srsLevel zaktualizowany
    card.dueAtIndex = globalIndex + SrsEngine.intervalFor(card.srsLevel, rating, rng)  // ✅ kolejka OK
    serviceScope.launch {
        flashcardRepo.updateSrs(card.flashcard.id, card.srsLevel, Clock.System.now().toString())
        // ❌ Result<FlashcardDto> z backendu jest ignorowany
        // ❌ card.flashcard (FlashcardDto) ma stare srsLevel i lastStudiedAt
    }
}
```

### SrsCard — struktura (problem: val flashcard)

```kotlin
// SrsEngine.kt:8-12
data class SrsCard(
    val flashcard: FlashcardDto,   // ❌ val — nie można podmienić
    var srsLevel: Float,
    var dueAtIndex: Int,
)
```

### publishState() — skąd biorą się stale dane w UI

```kotlin
// LearningService.kt:422-432
private fun publishState(phase: LearningPhase = LearningPhase.IDLE, card: FlashcardDto? = null) {
    state.value = LearningState(
        isActive = true,
        isPlaying = isPlaying,
        flashcards = srsQueue.map { it.flashcard },   // ← mapuje FlashcardDto ze stale srsLevel
        currentIndex = 0,
        phase = phase,
        currentCard = card,                           // ← przekazywana fiszka też stale
        playbackSpeed = playbackSpeed,
    )
}
```

### FlashcardDto — pola SRS

```kotlin
// ApiModels.kt:50-81
@Serializable
data class FlashcardDto(
    val id: String,
    // ...
    @SerialName("srs_level")
    val srsLevel: Float = 0f,               // musi być odświeżony po ocenie

    @SerialName("last_studied_at")
    val lastStudiedAt: String? = null,      // musi być odświeżony po ocenie
) {
    fun decayLevel(now: Instant = Clock.System.now()): Float { /* exponential decay */ }
}
```

`decayLevel()` używa **obu** pól — stare `srsLevel` i brak `lastStudiedAt` = błędny wskaźnik w `SrsLevelIndicator`.

### Backend zwraca pełną fiszkę — można użyć response

```kotlin
// FlashcardRepository.kt:29-33
suspend fun updateSrs(id: String, srsLevel: Float, lastStudiedAt: String): Result<FlashcardDto> =
    runCatching {
        val response = api.updateFlashcard(id, FlashcardUpdateRequest(srsLevel = srsLevel, lastStudiedAt = lastStudiedAt))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }
// ↑ Zwraca Result<FlashcardDto> z pełnymi danymi — w tym nowy srs_level i last_studied_at
```

Backend (Rust) przy `PUT /flashcards/{id}` robi `RETURNING` wszystkich kolumn włącznie z `srs_level` i `last_studied_at` i zwraca HTTP 200 z pełną fiszką.

## Code References

- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:343-349` — `applyRating()` — miejsce naprawy
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngine.kt:8-12` — `SrsCard` — zmiana `val` → `var`
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:50-81` — `FlashcardDto` z polami srs
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/FlashcardRepository.kt:29-33` — `updateSrs()` zwraca `Result<FlashcardDto>`
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:422-432` — `publishState()` mapuje `srsQueue`
- `apps/backend/src/handlers/flashcards.rs:96-135` — handler PUT `/flashcards/{id}` z RETURNING

## Architecture Insights

- `SrsCard` to wrapper wokół niemutowalnego `FlashcardDto`, celowo oddzielający logikę SRS (mutowalne `srsLevel`, `dueAtIndex`) od danych sieciowych. Zmieniając `flashcard: val` → `var`, pozwalamy podmienić cały obiekt DTO bez naruszania niemutowalności samego `FlashcardDto`.
- Alternatywą do `val → var` byłoby trzymanie `newSrsLevel: Float?` i `newLastStudiedAt: String?` bezpośrednio w `SrsCard` i scalanie przy `publishState()`, ale to komplikuje logikę bez wyraźnych korzyści.
- Odpowiedź backendu (`Result<FlashcardDto>`) jest już dostępna w `updateSrs()` — można ją opcjonalnie użyć zamiast lokalnie skalkulowanych wartości, ale ze względu na opóźnienie sieciowe lepiej zaktualizować lokalnie natychmiast i traktować response jako potwierdzenie.

## Open Questions

- Czy `SrsLevelIndicator` powinien pokazywać `decayLevel()` po ocenie, czy może "nagłe" przeskoczenie poziomu jest pożądanym UX? (Zmiana jest subtelna — decay trwa dni, nie minuty.)
- Czy przy błędzie sieciowym (`Result.failure`) należy cofnąć lokalną zmianę `card.flashcard`? Aktualnie błąd jest ignorowany.
