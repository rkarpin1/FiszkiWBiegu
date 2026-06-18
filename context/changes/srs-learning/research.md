---
date: 2026-06-18T00:00:00+02:00
researcher: Claude Sonnet 4.6
git_commit: 8fd84d87d4c111a00a9f13f85251ca7adf8df35a
branch: MVP
repository: FiszkiWBiegu
topic: "Algorytm SRS do kolejkowania słów w sesji nauki"
tags: [research, srs, learning, algorithm, flashcards, queue]
status: complete
last_updated: 2026-06-18
last_updated_by: Claude Sonnet 4.6
last_updated_note: "Decyzja: indeksowy algorytm (pierwotna propozycja) — prostszy i wystarczający dla MVP"
---

# Research: Algorytm SRS do kolejkowania słów w sesji nauki

**Date**: 2026-06-18  
**Git Commit**: 8fd84d87d4c111a00a9f13f85251ca7adf8df35a  
**Branch**: MVP  
**Repository**: FiszkiWBiegu

## Research Question

Zaproponowanie algorytmu SRS dla aplikacji do nauki słówek podczas biegu. Lista słów odtwarzana jest w pętli. Po każdym odsłuchaniu użytkownik może:
- nacisnąć "nie wiem" → słowo wraca natychmiast (zaraz po następnym)
- nic nie nacisnąć (po prostu odsłuchał) → "znam, ale uczę się dalej"
- nacisnąć "Wiem!" → słowo odkłada się daleko lub wypada z sesji

Każda fiszka przechowuje dane SRS w DB. Po każdym odegraniu SRS jest aktualizowany lokalnie i wysyłany na serwer (fire & forget).

## Summary

Baza kodu nie ma jeszcze żadnej implementacji SRS — ani w bazie danych, ani w warstwie danych, ani w logice sesji. Przyciski "Wiem!" i "Nie wiem" istnieją w UI jako wyłączone stubs. Pętla odtwarzania to czysty round-robin. Zmiany wymagane są we wszystkich trzech warstwach: DB (migracja 008), backend API (rozszerzenie PUT), frontend shared (model + algorytm), Android UI (aktywacja przycisków).

## Detailed Findings

### Stan aktualny — Backend

**Tabela `flashcards`** (po wszystkich migracjach 001–007):
```sql
id, collection_id, source_text, target_text, position, created_at
```
Brak jakichkolwiek pól SRS. Ostatnia migracja (007) tylko przemianowała kolumny.

**PUT /flashcards/{id}** (`apps/backend/src/models.rs:65-69`):
```rust
pub struct FlashcardUpdateRequest {
    pub source_text: Option<String>,
    pub target_text: Option<String>,
}
```
Endpoint przyjmuje wyłącznie tekst — nie można zaktualizować danych SRS.

**GET /collections/{id}/learning** (`apps/backend/src/handlers/learning.rs:21`):
Zwraca wszystkie fiszki z kolekcji posortowane po `position ASC`. Brak logiki SRS w kolejności.

### Stan aktualny — Frontend (shared)

**FlashcardDto** (`apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:47-62`):
```kotlin
data class FlashcardDto(
    val id: String,
    val collectionId: String,
    val sourceText: String,
    val targetText: String,
    val position: Int,
    val createdAt: String,
)
```
Brak pól SRS.

**FlashcardRepository** (`...data/repository/FlashcardRepository.kt`):
- `update(id, sourceText, targetText)` — tylko tekst, brak SRS
- `getLearningSession(collectionId)` — pobiera z `/collections/{id}/learning`

**Lokalna baza danych:** Projekt NIE ma Room ani SQLDelight. `multiplatform-settings` jest używane wyłącznie do przechowywania JWT tokena. Wszystkie dane fiszek żyją w pamięci (StateFlow).

### Stan aktualny — Android / LearningService

**Pętla odtwarzania** (`apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:259-300`):
```kotlin
private suspend fun CoroutineScope.playLoop() {
    while (isActive && flashcards.isNotEmpty()) {
        val card = flashcards[currentIndex]
        // ... fazy: IDLE → SPEAKING_SOURCE → ANSWER → SPEAKING_TARGET → REPEATING
        currentIndex = (currentIndex + 1) % flashcards.size  // line 298
    }
}
```
Czysty round-robin — brak priorytetu, brak shuffle.

**Przyciski UI** (`apps/frontend/shared/src/commonMain/kotlin/.../screens/learning/LearningScreen.kt:328-355`):
Przyciski "Nie wiem" i "Wiem!" istnieją w Compose UI, ale są **wyłączonymi stubami** — brak `.clickable()`, kolor `c.mute2`, brak callbacku do ViewModelu.

**LearningController interface** (`...screens/learning/LearningController.kt:17-25`):
```kotlin
interface LearningController {
    val state: StateFlow<LearningState>
    fun start(collection: CollectionDto, flashcards: List<FlashcardDto>)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun stop()
}
```
Brak metody `rate(flashcardId, rating)` lub podobnej.

## Proponowany Algorytm SRS

### Filozofia

Algorytm działa w trybie **sesji ciągłej** (bieg), nie kalendarzowego SRS (Anki). Zamiast "za ile dni powtórzyć" zarządza **pozycją w kolejce** — "za ile kart powtórzyć". Uproszczona wersja SM-2 dostosowana do realiów aplikacji.

### Dane SRS na fiszkę

```
srs_level: Float    — znajomość, 0.0 (nie wiem) do 1.0 (bardzo dobrze)
interval: Int       — za ile kart od teraz ponownie pokazać
repetitions: Int    — ile razy z rzędu oceniono pozytywnie
```

### Oceny i ich efekt

| Akcja użytkownika | Ocena | Zmiana srs_level | Nowa pozycja w kolejce |
|---|---|---|---|
| Naciśnięcie "nie wiem" | `DONT_KNOW` | `max(0.0, level - 0.2)` | currentIndex + 2 (zaraz po następnej) |
| Nic nie naciśnięto | `KNOW` | `min(1.0, level + 0.05)` | currentIndex + interval (gdzie interval = max(3, level×10).toInt()) |
| Naciśnięcie "Wiem!" | `KNOW_WELL` | `min(1.0, level + 0.2)` | currentIndex + interval (gdzie interval = max(10, level×20).toInt()), jeśli level > 0.85 → usuń z sesji |

### Wzór na interval

```kotlin
fun calculateInterval(level: Float, rating: Rating): Int = when (rating) {
    DONT_KNOW -> 1
    KNOW      -> maxOf(3, (level * 10).toInt())
    KNOW_WELL -> maxOf(10, (level * 20).toInt())
}
```

Przykłady:
- level=0.0, DONT_KNOW → interval=1 (zaraz po następnej)
- level=0.3, KNOW       → interval=3
- level=0.5, KNOW       → interval=5
- level=0.5, KNOW_WELL  → interval=10
- level=0.9, KNOW_WELL  → interval=18 lub usunięcie z sesji

### Zarządzanie kolejką w pamięci

Zamiast indeksu po tablicy, `LearningService` zarządza **kolejką priorytetową** reprezentowaną jako `MutableList<SrsCard>` gdzie każdy element ma `dueAtIndex: Int`.

```kotlin
data class SrsCard(
    val flashcard: FlashcardDto,
    var srsLevel: Float,
    var interval: Int,
    var repetitions: Int,
    var dueAtIndex: Int,  // numer karty globalnie, od kiedy pokazać
)
```

Algorytm pracy:
1. Init: wczytaj fiszki z serwera (z polami SRS), posortuj `srsLevel ASC` (najpierw nieznane)
2. Dla każdego kroku `globalIndex++`: weź pierwsze `SrsCard` gdzie `dueAtIndex <= globalIndex`
3. Po ocenie: aktualizuj `srsLevel`, wylicz nowe `dueAtIndex = globalIndex + interval`, wstaw z powrotem do kolejki, wyślij SRS na serwer (fire & forget)
4. Jeśli `KNOW_WELL && srsLevel > 0.85`: usuń kartę z sesji całkowicie

### Timeout dla "Znam" (brak akcji)

Użytkownik nic nie naciska = KNOW. LearningService po zakończeniu fazy `SPEAKING_TARGET` + `REPEATING` (obecne fazy) automatycznie traktuje brak interakcji jako ocenę KNOW i przechodzi dalej. Nie trzeba osobnego timeouta — obecna pętla wystarczy, wywoła `onCardCompleted(KNOW)` po zakończeniu audio.

## Code References

- `apps/backend/src/models.rs:65-69` — FlashcardUpdateRequest (tylko source/target)
- `apps/backend/src/handlers/flashcards.rs:96-130` — handler PUT /flashcards/{id}
- `apps/backend/src/handlers/learning.rs:21` — GET learning, sort by position
- `apps/backend/migrations/` — migracje 001-007, brak SRS; następna: 008
- `apps/frontend/shared/src/commonMain/kotlin/.../data/api/ApiModels.kt:47-62` — FlashcardDto (brak SRS)
- `apps/frontend/shared/src/commonMain/kotlin/.../data/api/ApiModels.kt:74-80` — FlashcardUpdateRequest (brak SRS)
- `apps/frontend/shared/src/commonMain/kotlin/.../data/repository/FlashcardRepository.kt` — update() bez SRS
- `apps/frontend/shared/src/commonMain/kotlin/.../screens/learning/LearningController.kt:17-25` — brak rate()
- `apps/frontend/shared/src/commonMain/kotlin/.../screens/learning/LearningScreen.kt:328-355` — przyciski (stubs)
- `apps/frontend/androidApp/src/main/kotlin/.../LearningService.kt:259-300` — playLoop, round-robin
- `apps/frontend/androidApp/src/main/kotlin/.../LearningService.kt:50-64` — akcje Intent (brak RATE)

## Architecture Insights

1. **Brak lokalnej bazy** — SRS state musi żyć w pamięci podczas sesji i być synchronizowany z serwerem. Nie dodajemy Room/SQLDelight (byłoby to poza MVP scope).
2. **Fire & forget** — po ocenie karty: aktualizuj lokalnie listę w pamięci, wyślij PUT asynchronicznie, nie czekaj na odpowiedź.
3. **Obsługa "Znam" (timeout)** — brak naciśnięcia przycisku = KNOW. Można to osiągnąć przez wywołanie `onCardCompleted(KNOW)` na końcu każdej iteracji pętli, jeśli żaden przycisk nie został naciśnięty.
4. **Kolejność inicjalna** — przy starcie sesji posortuj fiszki `srsLevel ASC`, żeby najpierw ćwiczyć najtrudniejsze.
5. **Usuwanie z sesji** — `KNOW_WELL` z `srsLevel > 0.85` usuwa kartę z aktywnej kolejki sesji, ale NIE usuwa jej z DB.
6. **Zgodność z architekturą** — nowa metoda `rate(flashcardId, rating)` wchodzi do interfejsu `LearningController` i jego implementacji `AndroidLearningController`. `LearningService` dodaje odpowiednią akcję Intent.

## Follow-up Research 2026-06-18 — Przegląd istniejących algorytmów SRS i adaptacja

### Przeanalizowane algorytmy

#### 1. Pimsleur Graduated Interval Recall (1967) — najbardziej zbliżony

Paul Pimsleur zaprojektował ten algorytm specjalnie dla **audio nauki języków**. Jako jedyny operuje krótkimi interwałami mierzonymi w sekundach i minutach — idealnie pasuje do sesji biegowej.

Konkretne interwały z pracy Pimsleur (1967):
```
5s → 25s → 2min → 10min → 1h → 5h → 1 dzień → 5 dni → 25 dni → 4 miesiące → 2 lata
```

Klucz: geometryczny wzrost (każdy interwał ≈ 5× poprzedni). W ramach 2h sesji biegowej pierwsze cztery stopnie (5s–10min) naturalnie wchodzą w zakres. Algorytm nie adaptuje trudności karty indywidualnie — każde słowo przechodzi tę samą sekwencję.

**Ograniczenie Pimsleur:** brak adaptacji do indywidualnych błędów. Sekwencja jest stała — jeśli słowo przysprawia problem, nie ma mechanizmu "cofania" do krótszych interwałów.

#### 2. SM-2 (SuperMemo 1987) — standard branżowy

Zaprojektowany dla dni, nie minut. Kluczowe wzory:

```
Interwały: I(1)=1d, I(2)=6d, I(n) = I(n-1) × EF
EF (Ease Factor): start=2.5, min=1.3
EF_new = EF + [0.1 − (5−q) × (0.08 + (5−q) × 0.02)]
  gdzie q ∈ {0..5} — ocena jakości odpowiedzi
Jeśli q < 3 → reset: n=0, interwał=1 dzień
```

Przy EF=2.5: 1d → 6d → 15d → 37d → 92d (wzrost geometryczny ~×2.5).

**Kluczowa idea do zapożyczenia:** Ease Factor — każda karta ma własny współczynnik łatwości, który rośnie przy dobrych odpowiedziach i maleje przy błędach. Interwały rosną geometrycznie, nie liniowo.

#### 3. FSRS (Free Spaced Repetition Scheduler, 2022) — nowoczesny standard Anki

Zastąpił SM-2 w Anki (od v23.10, 2023). 20–30% mniej powtórek niż SM-2 przy tej samej retencji.

Trzy parametry na kartę:
- **Stability (S)** — liczba dni, po których prawdopodobieństwo przypomnienia spada do 90%
- **Difficulty (D)** — wrodzona trudność materiału (1–10)
- **Retrievability (R)** — aktualne prawdopodobieństwo przypomnienia = f(S, czas od ostatniej powtórki)

`R = 0.9^(t/S)` — wykładniczy spadek, gdzie `t` = dni od powtórki, `S` = stabilność.

**Kluczowa idea do zapożyczenia:** Stability rośnie po każdej udanej powtórce, resetuje się po błędzie. Stability zastępuje SM-2's "interval" jako centralny parametr — opisuje siłę śladu pamięciowego, nie tylko harmonogram.

#### 4. System Leitner — pudełkowy model sesji

Karty w 5 pudełkach. Pudełko 1 = co chwilę, pudełko 5 = rzadko. Błąd → cofnięcie do pudełka 1. Poprawna odpowiedź → awans do następnego pudełka.

**Kluczowa idea do zapożyczenia:** dyskretny poziom (1–5 zamiast 0.0–1.0) jako prosty stan sesji, niezależny od globalnego harmonogramu. Każda ocena ma clear, deterministyczny efekt na pozycję karty.

---

### Algorytm FiszkiWBiegu — wersja finalna (Pimsleur + SM-2/FSRS hybrid)

#### Filozofia

Łączymy:
- **Pimsleur**: geometryczne interwały w minutach, zaprojektowane pod audio sesję
- **SM-2**: Ease Factor adaptujący się do historii odpowiedzi per karta
- **FSRS**: pojęcie Stability jako siły śladu pamięciowego (zamiast "ile kart od teraz")

Dwa tryby działania:
1. **In-session** (w trakcie biegu): interwały w minutach, zarządzane przez `nextReviewAt`
2. **Cross-session** (między biegami): ten sam `nextReviewAt` w DB naturalnie działa jako "kiedy pokazać w kolejnej sesji"

#### Pola SRS per fiszka (migracja 008)

```sql
srs_level         REAL    NOT NULL DEFAULT 0.0,   -- 0.0..1.0, siła znajomości
srs_stability_min REAL    NOT NULL DEFAULT 2.0,   -- bieżący interwał w minutach
srs_next_review_at TIMESTAMPTZ,                    -- NULL = nigdy nie widziana
```

`srs_level` ≈ EF/siła znajomości. `srs_stability_min` ≈ Stability z FSRS (ale w minutach). `srs_next_review_at` ≈ konkretny czas następnej powtórki.

#### Wzory — core scheduling

```kotlin
fun schedule(
    level: Float,           // 0.0..1.0
    stabilityMin: Float,    // aktualny interwał w minutach
    rating: Rating,
    now: Instant,
): SrsUpdate {
    // Ease Factor inspirowany SM-2: 1.5..2.5 zależnie od poznaości
    val easeFactor = 1.5f + level  // poziom 0.0 → EF=1.5; poziom 1.0 → EF=2.5

    val newStability = when (rating) {
        DONT_KNOW -> maxOf(1f, stabilityMin * 0.5f)       // kara: cofnij o połowę, min 1 min
        KNOW      -> minOf(120f, stabilityMin * easeFactor) // wzrost ×EF, max 2h
        KNOW_WELL -> minOf(240f, stabilityMin * easeFactor * 1.3f) // bonus ×30%, max 4h
    }

    val newLevel = when (rating) {
        DONT_KNOW -> maxOf(0f, level - 0.2f)
        KNOW      -> minOf(1f, level + 0.05f)
        KNOW_WELL -> minOf(1f, level + 0.15f)
    }

    val nextReviewAt = now + newStability.minutes
    return SrsUpdate(newLevel, newStability, nextReviewAt)
}
```

#### Pimsleur-aligned — konkretne wartości

Dla nowej karty (level=0.0, stability=2.0 min, EF=1.5):

```
Sekwencja "uczę się" (same KNOW):
  powtórka 0: po 2 min  (start)
  powtórka 1: po 3 min  (2 × 1.5)
  powtórka 2: po 4.5 min
  powtórka 3: po 7 min
  powtórka 4: po 10 min
  powtórka 5: po 15 min
  powtórka 6: po 23 min
  powtórka 7: po 34 min → przekracza 2h sesję, pojawi się jutro

Sekwencja Pimsleur dla porównania:
  2min → 10min → 1h → 5h → 1 dzień
  (wzrost ×5)

Nasz wzrost ×1.5 jest bardziej stopniowy — lepsza adaptacja do błędów.
```

Dla lepiej poznanej karty (level=0.5, stability=10 min, EF=2.0):
```
KNOW:      10 × 2.0 = 20 min
KNOW_WELL: 10 × 2.0 × 1.3 = 26 min
DONT_KNOW: 10 × 0.5 = 5 min
```

Dla bardzo dobrze poznanej (level=0.9, stability=60 min, EF=2.4):
```
KNOW:      60 × 2.4 = 144 min → cap 120 min → jutro
KNOW_WELL: 60 × 2.4 × 1.3 = 187 min → cap 240 min → pojutrze
DONT_KNOW: 60 × 0.5 = 30 min (powróci pod koniec sesji)
```

#### Zarządzanie kolejką

```kotlin
fun pickNext(cards: List<SrsCard>, now: Instant): SrsCard {
    val due = cards.filter { it.nextReviewAt <= now }
    return if (due.isNotEmpty()) {
        // spośród zaległych: najpierw najtrudniejsza (najniższy srs_level)
        due.minBy { it.srsLevel }
    } else {
        // nic nie zaległe → weź tę, która za chwilę wróci
        // (brak przerw w audio — karta grana nieco wcześniej niż zaplanowano)
        cards.minBy { it.nextReviewAt }
    }
}
```

#### Init sesji

```kotlin
fun initSession(flashcards: List<FlashcardDto>, now: Instant): List<SrsCard> {
    return flashcards.map { card ->
        SrsCard(
            flashcard = card,
            srsLevel = card.srsLevel,
            stabilityMin = card.srsSt abilityMin.takeIf { it > 0f } ?: 2f,
            // null nextReviewAt = nigdy nie widziana → traktuj jako zaległą
            nextReviewAt = card.srsNextReviewAt ?: now,
        )
    }.sortedBy { it.nextReviewAt }  // zaległe na początek
}
```

#### Wypadnięcie z sesji

Karta "wylatuje" z aktywnej kolejki gdy po `KNOW_WELL` nowy `nextReviewAt` przekroczy `sessionEndAt` (czas startu sesji + 2h). Karta zostaje w DB z zapisanym `nextReviewAt` — wróci w kolejnej sesji.

#### Porównanie z Pimsleur (tabela)

| Pimsleur (stałe) | FiszkiWBiegu (adaptive) | Sytuacja |
|---|---|---|
| 2 min | 1–3 min | pierwsze powtórzenie |
| 10 min | 5–15 min | drugie poprawne |
| 1h | 30–60 min | trzecie poprawne |
| 5h | >2h (kolejna sesja) | czwarte poprawne |
| brak adaptacji | DONT_KNOW → cofnij stabilność | błąd |

Nasz algorytm jest **bardziej adaptacyjny** niż Pimsleur (reaguje na błędy) i **bardziej realny** niż SM-2/FSRS (działa w minutach, nie dniach). Zachowuje geometryczny wzrost Pimsleur dla dobrze znanych kart.

## Follow-up Research 2026-06-18 — Algorytm czasowy zamiast indeksowego

### Dlaczego czas, nie indeks

Sesja może trwać do 2 godzin. W tym czasie nawet "bardzo dobrze" znane słowa zostaną odtworzone — co jest **pożądane**, bo właśnie o to chodzi w krzywej zapominania Ebbinghausa: powtórka we właściwym momencie utrwala ślad pamięciowy. Indeks karty jest arbitralny (zależy od tempa biegu, liczby kart), natomiast czas koreluje bezpośrednio z procesem zapominania.

### Krzywa zapominania — podstawy

Ebbinghaus: retencja spada eksponencjalnie `R = e^(-t/S)` gdzie `t` = czas od nauki, `S` = "siła" śladu pamięciowego. Optymalny moment powtórki to tuż przed progiem retencji (~70%). Dla nowego materiału: ~10 min. Po pierwszej udanej powtórce: ~30 min. Po kolejnych: godziny, dni.

W kontekście 2h sesji biegowej te same rzędy wielkości mają sens:

| Sytuacja | Optymalny interwał powtórki |
|---|---|
| Właśnie się pomyliłem | ~1 min (zanim zapomnę co usłyszałem) |
| Znam, ale niepewnie | 5–15 min |
| Znam dobrze | 20–60 min |
| Opanuję perfekcyjnie | następna sesja (pomijamy w tej) |

### Nowe pola SRS na fiszkę

```
srs_level: Float        — znajomość, 0.0 do 1.0
next_review_at: Instant — kiedy pokazać ponownie (UTC)
```

`interval` i `repetitions` mogą być wyliczone z historii lub pominięte w MVP — wystarczą `srs_level` i `next_review_at`.

### Formuła interwałów (minuty)

```kotlin
fun nextReviewDelay(rating: Rating, level: Float): Duration = when (rating) {
    DONT_KNOW -> 1.minutes                     // zawsze 1 minuta
    KNOW      -> (3f + level * 15f).minutes    // 3..18 minut
    KNOW_WELL -> (10f + level * 60f).minutes   // 10..70 minut
}
```

Przykłady konkretne (zakładając ~30 s/kartę):

| level | Ocena | Delay | Karty do następnej |
|---|---|---|---|
| 0.0 | DONT_KNOW | 1 min | ~2 karty |
| 0.2 | DONT_KNOW | 1 min | ~2 karty |
| 0.0 | KNOW | 3 min | ~6 kart |
| 0.5 | KNOW | 10.5 min | ~21 kart |
| 0.8 | KNOW | 15 min | ~30 kart |
| 0.0 | KNOW_WELL | 10 min | ~20 kart |
| 0.5 | KNOW_WELL | 40 min | ~80 kart |
| 0.85 | KNOW_WELL | 61 min | ~122 kart (koniec sesji) |
| >0.85 | KNOW_WELL | sesja = 120 min → nie wróci | wypadnięcie z sesji |

### Zmiana srs_level

```kotlin
fun updateLevel(current: Float, rating: Rating): Float = when (rating) {
    DONT_KNOW -> maxOf(0f, current - 0.2f)
    KNOW      -> minOf(1f, current + 0.05f)
    KNOW_WELL -> minOf(1f, current + 0.2f)
}
```

### Zarządzanie kolejką — mechanizm czasowy

Zamiast `dueAtIndex: Int` każda karta ma `nextReviewAt: Instant`.

```kotlin
data class SrsCard(
    val flashcard: FlashcardDto,
    var srsLevel: Float,
    var nextReviewAt: Instant,
)
```

**Algorytm wyboru następnej karty:**
```kotlin
fun pickNext(cards: List<SrsCard>, now: Instant): SrsCard {
    val due = cards.filter { it.nextReviewAt <= now }
    return if (due.isNotEmpty()) {
        due.minBy { it.srsLevel }          // spośród zaległych: najpierw najtrudniejsza
    } else {
        cards.minBy { it.nextReviewAt }    // nic nie jest zaległe: weź tę, która za chwilę
    }
}
```

**Fallback "nic nie zaległe":** Przy małej liczbie kart i długich interwałach może się zdarzyć, że wszystkie karty mają `nextReviewAt` w przyszłości. Zamiast ciszy bierzemy kartę z najwcześniejszym `nextReviewAt` i odtwarzamy ją nieco wcześniej — użytkownik biega i potrzebuje ciągłości.

**Init sesji:** Przy starcie wczytaj fiszki z serwera (z polami SRS). Jeśli `next_review_at` jest w przeszłości (zaległa) lub null → `nextReviewAt = now`. Posortuj zaległe `srsLevel ASC` (najpierw najtrudniejsze).

**Wypadnięcie z sesji:** Gdy `srsLevel > 0.85` i ocena `KNOW_WELL` → ustaw `nextReviewAt = now + 2.hours` (gwarantuje brak powtórki w tej sesji). W DB zapisz ten timestamp; następna sesja wczyta go i jeśli minęło dużo czasu — znowu pokaże.

### Pola DB do dodania w migracji 008

```sql
ALTER TABLE flashcards
  ADD COLUMN srs_level REAL NOT NULL DEFAULT 0.0,
  ADD COLUMN srs_next_review_at TIMESTAMPTZ;
```

`srs_next_review_at = NULL` oznacza "nigdy nie ćwiczone" → traktuj jak `now` przy starcie sesji.

### Endpoint SRS

Rozszerzyć istniejący `PUT /flashcards/{id}`:

```rust
pub struct FlashcardUpdateRequest {
    pub source_text: Option<String>,
    pub target_text: Option<String>,
    pub srs_level: Option<f32>,
    pub srs_next_review_at: Option<DateTime<Utc>>,
}
```

Alternatywnie dedykowany `PATCH /flashcards/{id}/srs` — czyściejszy, ale dodaje endpoint. W MVP prostszy jest wariant z rozszerzeniem istniejącego PUT.

## Follow-up Research 2026-06-18 — Decyzja: algorytm indeksowy (pierwotna propozycja)

Po przeanalizowaniu Pimsleur, SM-2, FSRS i podejścia czasowego — powrót do pierwszej propozycji. Uzasadnienie:

- Algorytm czasowy (minuty) wymaga śledzenia `Instant` i zarządzania timerami w `LearningService` — komplikuje implementację.
- W praktyce sesja biegowa ma stałe tempo (~30s/kartę), więc indeks karty i czas są prawie równoważne.
- Prostszy kod, łatwiejszy do zrozumienia i debugowania. MVP scope.

### Finalny algorytm — indeksowy

**Pola SRS per fiszka:**

| Pole | Typ | Default | Opis |
|---|---|---|---|
| `srs_level` | REAL | 0.0 | Znajomość, 0.0 (nie znam) → 1.0 (znam bardzo dobrze) |

> `interval` i `repetitions` nie są przechowywane w DB — `interval` jest wyliczany na bieżąco z `srs_level`, `repetitions` nie wchodzi do wzoru.

**Reguły kolejkowania:**

| Akcja użytkownika | Zmiana srs_level | Wstawienie do kolejki |
|---|---|---|
| Naciśnięcie "nie wiem" | `max(0.0, level − 0.2)` | pozycja `currentIndex + 2` (zaraz po następnej) |
| Nic (odsłuchał) | `min(1.0, level + 0.05)` | pozycja `currentIndex + max(3, (level × 10).toInt())` |
| Naciśnięcie "Wiem!" | `min(1.0, level + 0.2)` | pozycja `currentIndex + max(10, (level × 20).toInt())`; gdy `level > 0.85` → usuń z sesji |

**Wzór na interval — z losowością:**

```kotlin
fun intervalFor(level: Float, rating: Rating, rng: Random = Random.Default): Int {
    val base = when (rating) {
        DONT_KNOW -> return 2                           // zawsze +2, bez jitter — ma wrócić ZARAZ
        KNOW      -> maxOf(3,  (level * 10f).toInt())  // 3..10
        KNOW_WELL -> maxOf(10, (level * 20f).toInt())  // 10..20
    }
    // jitter proporcjonalny: ±20% base, min ±1
    val jitter = maxOf(1, (base * 0.2f).toInt())
    return maxOf(1, base + rng.nextInt(-jitter, jitter + 1))
}
```

`DONT_KNOW` nie ma jittera — sens całego mechanizmu polega na tym, że słowo wraca **zaraz**. Losowość w KNOW/KNOW_WELL zapobiega temu, żeby po wielu sesjach karty nie tworzyły stałych "grup" odtwarzanych zawsze razem.

**Przykłady:**

| level | DONT_KNOW | KNOW | KNOW_WELL |
|---|---|---|---|
| 0.0 | +2 | +3 | +10 |
| 0.3 | +2 | +3 | +10 |
| 0.5 | +2 | +5 | +10 |
| 0.7 | +2 | +7 | +14 |
| 0.9 | +2 | +9 | +18 (lub wypad z sesji) |

**Struktura danych sesji:**

```kotlin
data class SrsCard(
    val flashcard: FlashcardDto,
    var srsLevel: Float,
    var dueAtIndex: Int,     // numer karty globalnie, od kiedy pokazać
)

// globalCardIndex rośnie z każdą odtworzoną kartą
fun pickNext(queue: List<SrsCard>, globalIndex: Int): SrsCard =
    queue.filter { it.dueAtIndex <= globalIndex }
         .minByOrNull { it.srsLevel }       // spośród zaległych: najtrudniejsza
    ?: queue.minBy { it.dueAtIndex }        // nic zaległe: weź najbliższą
```

**Init sesji — z losowością:**

Cele: najtrudniejsze karty pojawiają się wcześniej, ale nie w sztywnej kolejności. Każda sesja wygląda inaczej.

```kotlin
fun initQueue(flashcards: List<FlashcardDto>, rng: Random = Random.Default): List<SrsCard> =
    flashcards
        .shuffled(rng)                                       // 1. wylosuj kolejność bazową
        .sortedBy { it.srsLevel + rng.nextFloat() * 0.3f }  // 2. posortuj wg level + jitter ±0.15
        .mapIndexed { i, card ->
            SrsCard(card, card.srsLevel, dueAtIndex = i)
        }
```

Efekt: karta z level=0.0 i karta z level=0.1 mogą zamienić się miejscami (jitter 0.3 to pozwala), ale karta z level=0.8 zawsze trafi dalej niż te dwie. Ogólna zasada "trudniejsze pierwsze" jest zachowana, ale nie ma deterministycznej kolejności.

**Persystencja:**

- `srs_level` jest jedynym polem w DB (migracja 008: `ALTER TABLE flashcards ADD COLUMN srs_level REAL NOT NULL DEFAULT 0.0`)
- Po każdej ocenie: zaktualizuj `srsLevel` lokalnie w kolejce, wyślij `PUT /flashcards/{id}` z nowym `srs_level` (fire & forget)
- Przy starcie kolejnej sesji: pobierz fiszki z API — `srs_level` jest odczytany z DB

**Odrzucone podejścia (dla dokumentacji):**

- *Czasowe (minuty)* — wymagałoby `Instant`/timerów w LearningService, zbędna złożoność w MVP
- *Pimsleur* — stałe interwały, brak adaptacji do błędów
- *SM-2/FSRS* — zaprojektowane pod dni, nie karty; overkill na MVP

## Open Questions

1. **Pola SRS w DB** — jakie konkretnie pola dodać w migracji 008? Propozycja: `srs_level REAL DEFAULT 0.0`, `srs_interval INT DEFAULT 1`, `srs_repetitions INT DEFAULT 0`. Czy `next_review_at TIMESTAMPTZ` jest potrzebne (kalendarza nie ma w MVP)?
2. **Timeout "Znam"** — czy ocena KNOW powinna być przyznawana po zakończeniu całego cyklu audio (SPEAKING_SOURCE → SPEAKING_TARGET × 3 → REPEATING), czy wcześniej (np. po SPEAKING_TARGET × 1)?
3. **Inicjalna wartość srs_level** — nowe fiszki mają 0.0. Czy fiszki bez historii SRS (dodane przed migracją) też powinny startować od 0.0?
4. **Endpoint SRS** — czy rozszerzamy istniejący PUT /flashcards/{id} o pola SRS, czy tworzymy dedykowany endpoint PATCH /flashcards/{id}/srs?
