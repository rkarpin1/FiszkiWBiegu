# SRS Decay — Krzywa zapominania — Krótki plan

> Pełny plan: `context/changes/srs-learning-2/plan.md`
> Poprzednia implementacja SRS: `context/archive/2026-06-18-srs-learning/`

## Co i dlaczego

Aktualny SRS traktuje fiszki jakby zawsze były "świeże" — poziom znajomości (srs_level) jest zachowany w DB bez wiedzy o tym, kiedy ostatnio karta była ćwiczona. Celem jest korekta: po pobraniu fiszek na starcie sesji nauki, poziom każdej karty jest obniżany proporcjonalnie do czasu, który minął od ostatniej nauki, wg krzywej Ebbinghausa. Karta nieuczona przez tydzień wraca do kolejki jako trudniejsza.

## Punkt wyjścia

`flashcards` ma `srs_level` (migration 008), ale brak `last_studied_at`. `SrsEngine.initQueue()` używa surowego poziomu bez decay. `FlashcardRepository.updateSrs()` wysyła tylko `srs_level` — bez timestampa.

## Pożądany stan końcowy

Po sesji nauki każda oceniona karta ma `last_studied_at` w DB. Przy starcie kolejnej sesji `SrsEngine` oblicza `decayLevel = level × e^(-t/stability)` — karta na poziomie 0.8 nieuczona 7 dni pojawia się z poziomem ~0.60, a więc wcześniej w kolejce. Użytkownik nieświadomie powtarza zapomniane słowa bez żadnych zmian w UI.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego |
|---|---|---|
| Formuła decay | Ebbinghaus: `level × e^(-t/stability)` | Matematycznie poprawna, smooth, standard SRS; stability = 1+level×29 (1–30 dni) |
| Dolna granica | 0.0 (brak) | Spójne z logiką SRS: brak pamięci = poziom 0 |
| Ustawianie `last_studied_at` | Client-explicit (Android wysyła `Clock.System.now()`) | Precyzyjniejszy czas oceny vs. czas synchronizacji |
| Null handling | Brak decay | Karta nigdy nieuczona ma `srsLevel=0.0` — nie ma czego decayować |
| Testowalność | `decayLevel` przyjmuje `now: Instant` | Deterministyczne testy bez mockowania zegara |

## Zakres

**W zakresie:**
- Migration 009: `last_studied_at TIMESTAMP WITH TIME ZONE NULL DEFAULT NULL`
- Rust: `Flashcard` + `FlashcardUpdateRequest` + 4 handlery (list, create, update, learning)
- Kotlin shared: `FlashcardDto`, `FlashcardUpdateRequest`, `FlashcardRepository.updateSrs()`, `SrsEngine.decayLevel()`, `SrsEngine.initQueue()`
- Android: `LearningService.applyRating()` — jeden parametr dodany do `updateSrs()`
- Testy: `SrsEngineTest.kt` — 5 nowych przypadków testowych dla `decayLevel`

**Poza zakresem:**
- Server-side auto-timestamp (wybrany client-explicit)
- Zmiana UI — decay jest transparentny dla użytkownika
- Backfill historycznych danych (`NULL` = brak decay)
- Nowe pole `srs_interval`, `srs_repetitions` — tylko `last_studied_at`

## Architektura / Podejście

```
DB: flashcards.last_studied_at (NULL)
         ↓
Backend PUT /flashcards/{id}: COALESCE($4, last_studied_at)
         ↓
GET /learning: zwraca last_studied_at w FlashcardDto
         ↓
SrsEngine.initQueue(): dla każdej karty:
    decayedLevel = decayLevel(srsLevel, lastStudiedAt, now)
    SrsCard(card, decayedLevel, dueAtIndex)
         ↓
applyRating(): newLevel(decayedLevel, rating) → updateSrs(id, level, now.toString())
```

Decay jest transparentny — żaden ekran UI, żaden ViewModel nie wymaga zmian.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|---|---|---|
| 1. Backend + Shared | Migration + Rust + Kotlin DTO + SrsEngine.decayLevel() + testy | SQL UPDATE ma 6 bindingów zamiast 5 — kolejność musi być dokładna |
| 2. Android | LearningService wysyła timestamp + E2E weryfikacja | Clock.System.now() musi być importowane z kotlinx.datetime |

**Wymagania wstępne:** Zarchiwizowana implementacja srs-learning (poziom bazowy)  
**Szacowany nakład pracy:** ~1–2 sesje, 2 fazy

## Otwarte ryzyka i założenia

- `TIMESTAMP WITH TIME ZONE` w PostgreSQL vs. `DateTime<Utc>` w chrono — sqlx obsługuje tę konwersję natywnie (feature `chrono` już w Cargo.toml)
- Karta z `srs_level=0.0` i `last_studied_at != null` — decay zwróci 0.0 (poprawne matematycznie: `0 × e^x = 0`)
- Precision: używamy minut dla lepszej granularności przy ręcznym testowaniu (zamiast pełnych dni)

## Kryteria sukcesu (podsumowanie)

- `GET /collections/{id}/learning` zwraca `last_studied_at` w każdej fiszce
- Karta z `srs_level=0.8` i `last_studied_at` sprzed 7 dni ma `SrsCard.srsLevel ≈ 0.60` na starcie sesji
- Po ocenieniu karty, `last_studied_at` jest aktualizowane w DB (widoczne przez API)
