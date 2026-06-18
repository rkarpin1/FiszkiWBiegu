# SRS Learning — Brief implementacji

## Co budujemy

Algorytm Spaced Repetition System (SRS) do kolejkowania fiszek podczas sesji nauki. Karty są odtwarzane w kolejności wyznaczonej przez poziom znajomości (`srs_level`) i globalny licznik indeksów — nie w kółko po kolei.

## Cel użytkownika

Biegnę i słucham fiszek. Jeśli słyszę słowo, którego nie znam — naciskam "Nie wiem" i słowo wraca zaraz. Jeśli słyszę słowo, które znam — nic nie robię, wraca później. Jeśli dobrze znam — naciskam "Wiem!" i wraca rzadko. Efektem jest skupienie na najtrudniejszych słowach przy jednoczesnym utrzymaniu powtórek tych znanych.

## Jedno nowe pole w DB

```sql
ALTER TABLE flashcards ADD COLUMN srs_level REAL NOT NULL DEFAULT 0.0;
```

Zakres 0.0 (nieznane) → 1.0 (bardzo dobrze znane). Nie ma dodatkowych pól: brak daty, brak licznika powtórek. Tylko poziom.

## Algorytm (indeksowy)

Każda karta ma `dueAtIndex` — numer sesji, przy którym powinna być odtworzona. `globalIndex` rośnie monotonnicznie przy każdej odtworzonej karcie.

| Akcja | Ocena | Interwał | Jitter |
|---|---|---|---|
| Naciśnięcie "Nie wiem" | DONT_KNOW | +2 | brak (zawsze dokładnie 2) |
| Brak akcji (odsłuchane) | KNOW | max(3, level×10) | ±20% |
| Naciśnięcie "Wiem!" | KNOW_WELL | max(10, level×20) | ±20% |

Na starcie sesji: karty są wymieszane losowo, następnie posortowane wg `srsLevel + jitter` — trudniejsze karty grają wcześniej. Żadna karta nigdy nie jest usuwana z kolejki.

**Wybór następnej karty**: spośród kart z `dueAtIndex ≤ globalIndex`, ta o najniższym `srsLevel`. Jeśli żadna nie jest zaległa — ta o najmniejszym `dueAtIndex`.

## Cztery fazy pracy

### Faza 1 — Backend (~1h)
- `008_add_srs_level.sql` — migracja
- `models.rs` — dodać `srs_level: f32` do `Flashcard` i `FlashcardUpdateRequest`
- `handlers/flashcards.rs` — rozszerzyć SQL PUT o COALESCE na `srs_level`

### Faza 2 — Shared (~2h)
- `ApiModels.kt` — `srsLevel: Float = 0f` w `FlashcardDto`, `srsLevel: Float? = null` w `FlashcardUpdateRequest`
- `FlashcardRepository.kt` — nowa metoda `updateSrs(id, srsLevel)` (fire & forget)
- **Nowy plik** `SrsEngine.kt` — `Rating` enum, `SrsCard` data class, `initQueue()`, `pickNext()`, `intervalFor()`, `newLevel()`
- `LearningController.kt` — `fun rate(Rating)` w interfejsie + `currentCard: FlashcardDto?` w `LearningState`
- `LearningViewModel.kt` — delegacja `rate()` do kontrolera

### Faza 3 — Android (~3h, najcięższe)
- `LearningService.kt`:
  - Pola: `srsQueue`, `globalIndex`, `currentSrsCard`, `cardRated`, `rng`, `flashcardRepo` (Koin inject)
  - Nowe stałe: `ACTION_RATE`, `EXTRA_RATING`
  - `ACTION_START` — init `SrsEngine.initQueue()`
  - `ACTION_RATE` — `applyRating()` + `tts?.stop()` + `playJob?.cancel()` + restart
  - `playLoop()` — przepisany: `pickNext()` zamiast round-robin, `cardRated` guard, KNOW na końcu cyklu
  - `applyRating()` — update karty w queue + `serviceScope.launch { repo.updateSrs() }`
  - `playRatingSound()` — `AudioManager.playSoundEffect()` dla KNOW_WELL i DONT_KNOW
- `AndroidLearningController.kt` — `rate()` przez Intent `ACTION_RATE`

### Faza 4 — UI (~1h)
- `LearningScreen.kt` — przyciski aktywne tylko w fazie `ANSWER`, `animateColorAsState` (czerwony/zielony ~300ms), `onDontKnow`/`onKnowWell` callbacki do ViewModel

## Kluczowy mechanizm: interrupt

Gdy przycisk jest naciśnięty w fazie ANSWER:
1. `cardRated = true` (guard przed podwójną oceną)
2. `applyRating(currentSrsCard!!, rating)` — karta dostaje nowy `dueAtIndex` i `srsLevel`
3. Dźwięk (`AudioManager`)
4. `tts?.stop()` + `playJob?.cancel()` — natychmiastowe przerwanie
5. `startPlayJob()` — restart pętli od nowej karty

Gdy karta odtworzona do końca bez akcji → `applyRating(card, Rating.KNOW)` na końcu pętli.

## Synchronizacja z serwerem

Fire & forget — po każdej ocenie: `serviceScope.launch { flashcardRepo.updateSrs(id, newLevel) }`. Błędy ignorowane. Brak lokalnej bazy danych — dane żyją w `srsQueue` (pamięć) przez czas sesji.

## Testy jednostkowe

`SrsEngineTest.kt` w `commonTest`:
- `DONT_KNOW` zawsze zwraca interwał 2
- Interwały `KNOW`/`KNOW_WELL` mieszczą się w oczekiwanym zakresie ±20%
- `initQueue` sortuje po `srsLevel` (losowo, ale deterministycznie dla ziarna)
- `pickNext` preferuje zaległą kartę z najniższym poziomem
