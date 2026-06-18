# SRS Learning Algorithm — Plan implementacji

## Przegląd

Implementacja algorytmu Spaced Repetition System (SRS) we wszystkich czterech warstwach stosu: baza danych, backend API, frontend shared, Android UI. Jedno nowe pole w DB (`srs_level`), algorytm indeksowy zarządzający kolejką kart w pamięci podczas sesji, synchronizacja z serwerem fire & forget.

## Analiza stanu obecnego

- Tabela `flashcards` nie ma żadnych pól SRS (kolumny: id, collection_id, source_text, target_text, position, created_at)
- `PUT /flashcards/{id}` przyjmuje wyłącznie `source_text` i `target_text`
- `FlashcardDto` i `FlashcardUpdateRequest` nie mają pól SRS
- `LearningController` nie ma metody `rate()`
- `LearningService.playLoop()` to czysty round-robin (`currentIndex + 1) % size`)
- Przyciski "Wiem!" i "Nie wiem" istnieją w `LearningScreen.kt:328-355` jako Compose `Box` bez `.clickable()` i w kolorze `c.mute2`
- Projekt nie ma lokalnej bazy danych (Room/SQLDelight) — dane fiszek żyją w pamięci

## Pożądany stan końcowy

Po wdrożeniu:
- Każda fiszka ma `srs_level` (0.0–1.0) w DB i w DTO
- `LearningService` zarządza `srsQueue: MutableList<SrsCard>` zamiast tablicy round-robin
- Podczas fazy `ANSWER` przyciski są aktywne i klikalną
- Naciśnięcie przycisku natychmiast przerywa bieżącą kartę, aplikuje ocenę i przechodzi do następnej
- Brak akcji (odsłuchanie do końca) = ocena KNOW
- Po ocenie: `srs_level` zaktualizowany lokalnie + `PUT /flashcards/{id}` fire & forget
- Kolor przycisku zmienia się chwilowo po naciśnięciu + dźwięk systemowy

### Kluczowe odkrycia:

- `LearningService.kt:163` — `flashcards: List<FlashcardDto>` zastępujemy `srsQueue: MutableList<SrsCard>`
- `LearningService.kt:275` — faza `ANSWER` zaczyna się po `SPEAKING_SOURCE`; w tej fazie przyciski aktywne
- `LearningService.kt:276` — `speakAndWait(card.targetText, ..., 0f)` gra target tekst cicho (mierzy czas) — okno ANSWER trwa tę chwilę + 800ms delay
- `AndroidLearningController.kt:28-37` — komunikacja przez Intent (startForegroundService) — `rate()` pójdzie tym samym wzorcem
- `handlers/flashcards.rs:104-114` — UPDATE SQL z COALESCE; dodanie `srs_level = COALESCE($3, flashcards.srs_level)` jest bezpieczne
- `LearningState` (LearningController.kt:9-15) — dodajemy pole `currentCard: FlashcardDto? = null` zamiast przebudowy `flashcards`/`currentIndex`

## Czego NIE robimy

- Nie dodajemy lokalnej bazy danych (Room/SQLDelight) — poza zakresem MVP
- Nie obsługujemy błędów synchronizacji z serwerem (fire & forget, ignorujemy błędy)
- Nie implementujemy kalendarza powtórek między sesjami (tylko `srs_level` rośnie/maleje)
- Nie dodajemy `srs_interval` ani `srs_repetitions` do DB — tylko `srs_level`
- Nie tworzymy nowego endpointu PATCH /srs — rozszerzamy istniejący PUT
- Nie usuwamy kart z kolejki gdy level > 0.85 — zostają z dużym interwałem

## Podejście do implementacji

Backend-first (migracja + API) → shared (model + SrsEngine + interfejs) → Android (logika sesji) → UI (aktywacja przycisków).

Każda faza może być niezależnie zatwierdzona i przetestowana przed kolejną. Faza 3 jest najcięższa — przepisuje serce `LearningService.playLoop()`.

## Krytyczne szczegóły implementacji

- **Interrupt mechanizm**: `ACTION_RATE` w `onStartCommand()` musi zapisać `currentSrsCard` (ustawiony na początku każdej iteracji) i wywołać `rateCard()` + `tts?.stop()` + `playJob?.cancel()` + `startPlayJob()`. Trzeba uważać: `cardRated: Boolean` zapobiega podwójnemu aplikowaniu oceny (raz przez przycisk, raz przez koniec cyklu).
- **globalIndex vs currentIndex**: `globalIndex` rośnie monotonnicznie (nigdy nie resetuje się), `currentIndex` jest usunięty z logiki sesji. `dueAtIndex` kart jest liczony względem `globalIndex`.
- **LearningState.flashcards**: Zachowujemy to pole — przekazujemy oryginalną listę FlashcardDto (bez SRS queue ordering) żeby UI nadal miało dostęp do rozmiaru kolekcji. Nowe pole `currentCard` zastępuje `flashcards[currentIndex]` jako "co teraz gra".
- **Koin w LearningService**: `FlashcardRepository` jest wstrzyknięty przez `by inject<FlashcardRepository>()` — działa bo serwis jest w Koin-enabled app. Dodajemy `KoinComponent` do klasy serwisu.

---

## Faza 1: Backend — migracja DB + rozszerzenie PUT /flashcards/{id}

### Przegląd

Dodanie kolumny `srs_level` do tabeli `flashcards` i rozszerzenie endpointu PUT o możliwość aktualizacji tego pola. Istniejące fiszki startują z `0.0`.

### Wymagane zmiany:

#### 1. Migracja 008

**Plik**: `apps/backend/migrations/008_add_srs_level.sql`

**Cel**: Dodać kolumnę `srs_level` do tabeli `flashcards`.

**Kontrakt**:
```sql
ALTER TABLE flashcards
    ADD COLUMN srs_level REAL NOT NULL DEFAULT 0.0;
```

#### 2. Model Flashcard — dodanie srs_level

**Plik**: `apps/backend/src/models.rs:49-57`

**Cel**: Rozszerzyć struct `Flashcard` o pole `srs_level` aby sqlx mapował je z DB i serializował do JSON.

**Kontrakt**: Dodać `pub srs_level: f32` po `position: i32`. Kolejność musi zgadzać się z kolejnością kolumn w RETURNING clause.

#### 3. FlashcardUpdateRequest — dodanie srs_level

**Plik**: `apps/backend/src/models.rs:65-69`

**Cel**: Umożliwić klientom przekazanie nowego `srs_level` w żądaniu PUT.

**Kontrakt**: Dodać `pub srs_level: Option<f32>` do struct `FlashcardUpdateRequest`.

#### 4. Handler update() — rozszerzenie SQL

**Plik**: `apps/backend/src/handlers/flashcards.rs:104-119`

**Cel**: Zaktualizować zapytanie UPDATE aby obsługiwało `srs_level = COALESCE($3, flashcards.srs_level)` i zwracało nowe pole w RETURNING.

**Kontrakt**:
```sql
UPDATE flashcards SET
    source_text = COALESCE($1, flashcards.source_text),
    target_text = COALESCE($2, flashcards.target_text),
    srs_level   = COALESCE($3, flashcards.srs_level)
FROM collections
WHERE flashcards.id = $4
  AND flashcards.collection_id = collections.id
  AND collections.user_id = $5
RETURNING flashcards.id, flashcards.collection_id, flashcards.source_text,
          flashcards.target_text, flashcards.position, flashcards.created_at,
          flashcards.srs_level
```
Bindingi: `$1=source_text`, `$2=target_text`, `$3=srs_level`, `$4=id`, `$5=user.id`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Migracja stosuje się czysto: `cd apps/backend && cargo run` startuje bez błędu migracji
- Build przechodzi: `cargo build`
- Testy backendu przechodzą: `cargo test`

#### Weryfikacja ręczna:

- `PUT /flashcards/{id}` z body `{"srs_level": 0.5}` zwraca 200 z `srs_level: 0.5` w odpowiedzi
- `PUT /flashcards/{id}` z body `{}` (bez srs_level) zwraca 200, `srs_level` bez zmian
- `GET /collections/{id}/learning` zwraca fiszki z polem `srs_level`

**Uwaga implementacyjna**: Po zakończeniu tej fazy i przejściu wszystkich automatycznych weryfikacji, zatrzymaj się tutaj, aby uzyskać ręczne potwierdzenie przed przejściem do Fazy 2.

---

## Faza 2: Frontend shared — FlashcardDto + SrsEngine + LearningController.rate()

### Przegląd

Rozszerzenie modelu danych o `srs_level`, implementacja algorytmu SRS jako niezależnego modułu `SrsEngine`, rozszerzenie interfejsu `LearningController` o metodę `rate()`.

### Wymagane zmiany:

#### 1. FlashcardDto — dodanie srs_level

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:47-62`

**Cel**: Odwzorować pole `srs_level` z API response.

**Kontrakt**: Dodać `@SerialName("srs_level") val srsLevel: Float = 0f` do `FlashcardDto`. Default 0f — kompatybilność wsteczna.

#### 2. FlashcardUpdateRequest — dodanie srs_level

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:74-80`

**Cel**: Umożliwić wysyłanie `srs_level` w PUT.

**Kontrakt**: Dodać `@SerialName("srs_level") val srsLevel: Float? = null` do `FlashcardUpdateRequest`.

#### 3. FlashcardRepository — metoda updateSrs()

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/FlashcardRepository.kt`

**Cel**: Dodać dedykowaną metodę do aktualizacji `srs_level` fiszki bez zmiany istniejącej sygnatury `update()`.

**Kontrakt**: Nowa suspend fun `suspend fun updateSrs(id: String, srsLevel: Float): Result<FlashcardDto>` — wywołuje `apiClient.updateFlashcard(id, FlashcardUpdateRequest(srsLevel = srsLevel))`.

#### 4. SrsEngine — algorytm

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngine.kt` (nowy plik)

**Cel**: Enkapsulować całą logikę SRS: enum ocen, model karty sesji, wzory kolejkowania.

**Kontrakt** — pełne sygnatury:

```kotlin
enum class Rating { DONT_KNOW, KNOW, KNOW_WELL }

data class SrsCard(
    val flashcard: FlashcardDto,
    var srsLevel: Float,
    var dueAtIndex: Int,
)

object SrsEngine {
    fun initQueue(flashcards: List<FlashcardDto>, rng: Random = Random.Default): MutableList<SrsCard>
    fun pickNext(queue: List<SrsCard>, globalIndex: Int): SrsCard
    fun intervalFor(level: Float, rating: Rating, rng: Random = Random.Default): Int
    fun newLevel(current: Float, rating: Rating): Float
}
```

Implementacja `intervalFor`:
```kotlin
fun intervalFor(level: Float, rating: Rating, rng: Random): Int {
    val base = when (rating) {
        DONT_KNOW -> return 2  // zawsze, bez jitter
        KNOW      -> maxOf(3,  (level * 10f).toInt())
        KNOW_WELL -> maxOf(10, (level * 20f).toInt())
    }
    val jitter = maxOf(1, (base * 0.2f).toInt())
    return maxOf(1, base + rng.nextInt(-jitter, jitter + 1))
}
```

Implementacja `initQueue`:
```kotlin
fun initQueue(flashcards: List<FlashcardDto>, rng: Random): MutableList<SrsCard> =
    flashcards
        .shuffled(rng)
        .sortedBy { it.srsLevel + rng.nextFloat() * 0.3f }
        .mapIndexed { i, card -> SrsCard(card, card.srsLevel, dueAtIndex = i) }
        .toMutableList()
```

Implementacja `pickNext`:
```kotlin
fun pickNext(queue: List<SrsCard>, globalIndex: Int): SrsCard =
    queue.filter { it.dueAtIndex <= globalIndex }
         .minByOrNull { it.srsLevel }
         ?: queue.minBy { it.dueAtIndex }
```

#### 5. LearningState — dodanie currentCard

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningController.kt:9-15`

**Cel**: Umożliwić UI pokazanie aktualnie granej karty bez zależności od `flashcards[currentIndex]`.

**Kontrakt**: Dodać `val currentCard: FlashcardDto? = null` do `LearningState`.

#### 6. LearningController — metoda rate()

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningController.kt:17-25`

**Cel**: Umożliwić ViewModelowi przekazanie oceny do kontrolera.

**Kontrakt**: Dodać `fun rate(rating: Rating)` do interfejsu `LearningController`.

#### 7. LearningViewModel — metoda rate()

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningViewModel.kt`

**Cel**: Udostępnić `rate()` dla UI.

**Kontrakt**: `fun rate(rating: Rating) = controller.rate(rating)`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Shared testy kompilują się: `./gradlew :shared:compileKotlinAndroid`
- Shared testy jednostkowe: `./gradlew :shared:test` (jeśli są testy SrsEngine — dodać)
- Build Android: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Brak regresji w ekranach flashcard (edycja, lista)

**Uwaga implementacyjna**: Po zakończeniu tej fazy, zatrzymaj się i zweryfikuj build przed Fazą 3.

---

## Faza 3: Android — LearningService (SRS queue) + AndroidLearningController + dźwięk

### Przegląd

Przepisanie logiki sesji w `LearningService`: zastąpienie round-robin kolejką SRS (`srsQueue + globalIndex`), obsługa `ACTION_RATE`, mechanizm interrupt karty, dźwięk systemowy po ocenie.

### Wymagane zmiany:

#### 1. LearningService — pola sesji

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:163-166`

**Cel**: Zastąpić `flashcards: List<FlashcardDto>` i `currentIndex: Int` strukturami SRS.

**Kontrakt**: Usunąć pola `flashcards` i `currentIndex`. Dodać:
```kotlin
private val srsQueue = mutableListOf<SrsCard>()
private var globalIndex = 0
private var currentSrsCard: SrsCard? = null
private var cardRated = false
private val rng = Random.Default
private val flashcardRepo: FlashcardRepository by inject()
```
Klasa musi implementować `KoinComponent`.

#### 2. LearningService — ACTION_RATE

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:53-60` (companion object)

**Cel**: Dodać stałe dla nowej akcji.

**Kontrakt**: 
```kotlin
const val ACTION_RATE = "pl.rkarpinski.fiszkiwbiegu.learning.RATE"
const val EXTRA_RATING = "rating"
```

#### 3. LearningService — obsługa ACTION_RATE w onStartCommand

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:203-226`

**Cel**: Gdy przycisk naciśnięty — zaaplikować ocenę do bieżącej karty, przerwać odtwarzanie, restartnąć.

**Kontrakt**:
```kotlin
ACTION_RATE -> {
    val rating = Rating.valueOf(intent.getStringExtra(EXTRA_RATING) ?: return START_STICKY)
    val card = currentSrsCard
    if (card != null && !cardRated) {
        cardRated = true
        applyRating(card, rating)
        playRatingSound(rating)
        tts?.stop()
        playJob?.cancel()
        if (isPlaying) startPlayJob()
    }
}
```

#### 4. LearningService — applyRating()

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt` (nowa metoda)

**Cel**: Zaktualizować kartę w kolejce + wysłać do serwera fire & forget.

**Kontrakt**:
```kotlin
private fun applyRating(card: SrsCard, rating: Rating) {
    card.srsLevel = SrsEngine.newLevel(card.srsLevel, rating)
    card.dueAtIndex = globalIndex + SrsEngine.intervalFor(card.srsLevel, rating, rng)
    serviceScope.launch {
        flashcardRepo.updateSrs(card.flashcard.id, card.srsLevel)
    }
}
```

#### 5. LearningService — playRatingSound()

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt` (nowa metoda)

**Cel**: Krótki dźwięk systemowy po ocenie — feedback dla użytkownika biegnącego.

**Kontrakt**:
```kotlin
private fun playRatingSound(rating: Rating) {
    val am = getSystemService(AUDIO_SERVICE) as AudioManager
    when (rating) {
        Rating.KNOW_WELL -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 1f)
        Rating.DONT_KNOW -> am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, 1f)
        Rating.KNOW      -> Unit  // brak dźwięku dla pasywnej oceny
    }
}
```

#### 6. LearningService — przepisanie ACTION_START

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:206-217`

**Cel**: Inicjalizacja SRS queue zamiast prostej listy.

**Kontrakt**: W gałęzi `ACTION_START`:
```kotlin
ACTION_START -> {
    val json = intent.getStringExtra(EXTRA_FLASHCARDS_JSON) ?: return START_STICKY
    collectionJson = intent.getStringExtra(EXTRA_COLLECTION_JSON)
    val allFlashcards = Json.decodeFromString<List<FlashcardDto>>(json)
    if (allFlashcards.isEmpty()) return START_NOT_STICKY
    srsQueue.clear()
    srsQueue.addAll(SrsEngine.initQueue(allFlashcards, rng))
    globalIndex = 0
    currentSrsCard = null
    // reszta bez zmian (ttsPlayer.updateCurrentItem, startPlayJob itd.)
}
```

#### 7. LearningService — przepisanie playLoop()

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:259-300`

**Cel**: Zastąpić round-robin kolejką SRS. Karta wybierana przez `SrsEngine.pickNext()`. Brak akcji = KNOW na końcu cyklu.

**Kontrakt** — nowa pętla:
```kotlin
private suspend fun CoroutineScope.playLoop() {
    val collection = collectionJson?.let { Json.decodeFromString<CollectionDto>(it) }
    while (isActive && srsQueue.isNotEmpty()) {
        if (!isPlaying) { delay(200); continue }

        val card = SrsEngine.pickNext(srsQueue, globalIndex)
        currentSrsCard = card
        cardRated = false
        globalIndex++

        publishState(LearningPhase.IDLE, card.flashcard)
        publishState(LearningPhase.SPEAKING_SOURCE, card.flashcard)
        ttsPlayer.updateCurrentItem(card.flashcard.toMediaItem(this@LearningService, collection?.sourceLanguage ?: "pl"))
        speakAndWait(card.flashcard.sourceText, Locale.forLanguageTag(collection?.sourceLanguage ?: "pl"))
        if (!isActive || !isPlaying) continue

        publishState(LearningPhase.ANSWER, card.flashcard)
        val timeForTargetText = speakAndWait(card.flashcard.targetText, Locale.forLanguageTag(collection?.targetLanguage ?: "en"), 0f)
        if (!isActive || !isPlaying) continue
        delay(800)
        if (!isActive || !isPlaying) continue

        repeat(3) {
            if (isActive && isPlaying) {
                publishState(LearningPhase.SPEAKING_TARGET, card.flashcard)
                ttsPlayer.updateCurrentItem(card.flashcard.toMediaItem(this@LearningService, collection?.targetLanguage ?: "en"))
                speakAndWait(card.flashcard.targetText, Locale.forLanguageTag(collection?.targetLanguage ?: "en"))
                if (isActive && isPlaying) {
                    publishState(LearningPhase.REPEATING, card.flashcard)
                    delay(timeForTargetText + 500)
                }
            }
        }
        if (!isActive || !isPlaying) continue

        // Brak oceny przez użytkownika = KNOW
        if (!cardRated) {
            applyRating(card, Rating.KNOW)
        }

        publishState(LearningPhase.IDLE, card.flashcard)
        delay(1000)
    }
}
```

#### 8. LearningService — przepisanie publishState()

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:365-373`

**Cel**: Przekazać `currentCard` do stanu.

**Kontrakt**:
```kotlin
private fun publishState(phase: LearningPhase = LearningPhase.IDLE, card: FlashcardDto? = null) {
    state.value = LearningState(
        isActive = true,
        isPlaying = isPlaying,
        flashcards = srsQueue.map { it.flashcard },
        currentIndex = 0,
        phase = phase,
        currentCard = card,
    )
}
```

#### 9. AndroidLearningController — implementacja rate()

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/AndroidLearningController.kt`

**Cel**: Przekazać ocenę użytkownika do LearningService przez Intent.

**Kontrakt**: Dodać metodę zgodnie z interfejsem LearningController:
```kotlin
override fun rate(rating: Rating) {
    context.startService(
        Intent(context, LearningService::class.java).apply {
            action = LearningService.ACTION_RATE
            putExtra(LearningService.EXTRA_RATING, rating.name)
        }
    )
}
```

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Build APK: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Sesja startuje i karty są odtwarzane w losowej kolejności (nie zawsze ta sama na początku)
- Naciśnięcie "Nie wiem" natychmiast przerywa kartę i przechodzi do następnej — ta sama karta wraca po ~2 kartach
- Naciśnięcie "Wiem!" przeskakuje kartę — wraca po ok. 10+ kartach
- Bez naciśnięcia — karta wraca po 3–10 kartach (zależnie od srs_level)
- Słychać dźwięk po naciśnięciu przycisku
- Logcat nie pokazuje crashy ani NullPointerException

**Uwaga implementacyjna**: Po zakończeniu tej fazy — pełna weryfikacja ręczna sesji przed przejściem do Fazy 4.

---

## Faza 4: UI — aktywacja przycisków w fazie ANSWER + feedback kolorystyczny

### Przegląd

Aktywowanie przycisków "Wiem!" i "Nie wiem" w `LearningScreen.kt` — wyłącznie w fazie `ANSWER`. Chwilowa zmiana koloru tła przycisku po naciśnięciu jako wizualne potwierdzenie.

### Wymagane zmiany:

#### 1. LearningScreen — aktywacja i klikanie przycisków

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningScreen.kt:328-355`

**Cel**: Przyciski są klikalną wyłącznie w fazie ANSWER; w innych fazach wyglądają jak dotychczas (mute, nieaktywne).

**Kontrakt**: Zastąpić dwa `Box` bez `clickable` na dwa `Box` z warunkiem na `state.phase == LearningPhase.ANSWER`. Użyć `animateColorAsState` dla chwilowego zapalenia koloru po kliknięciu.

Sygnatura callbacków w sygnaturze composable `LearningScreen`:
```kotlin
fun LearningScreen(
    state: LearningState,
    onDontKnow: () -> Unit,   // nowy
    onKnowWell: () -> Unit,   // nowy
    // ... istniejące parametry ...
)
```

Logika w przyciskach:
```kotlin
val isAnswerPhase = state.phase == LearningPhase.ANSWER

// "Nie wiem" button:
var dontKnowPressed by remember { mutableStateOf(false) }
val dontKnowBg by animateColorAsState(
    if (dontKnowPressed) Color.Red.copy(alpha = 0.3f) else scheme.surfaceVariant,
    animationSpec = tween(300),
    finishedListener = { dontKnowPressed = false }
)
Box(
    modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .clip(MaterialTheme.shapes.large)
        .background(dontKnowBg)
        .border(1.dp, if (isAnswerPhase) scheme.outline else scheme.outlineVariant, MaterialTheme.shapes.large)
        .then(if (isAnswerPhase) Modifier.clickable {
            dontKnowPressed = true
            onDontKnow()
        } else Modifier),
    contentAlignment = Alignment.Center,
) {
    Text("Nie wiem", color = if (isAnswerPhase) scheme.onSurface else c.mute2)
}
// analogicznie "Wiem!" z zielonym kolorem
```

#### 2. LearningScreen — podpięcie callbacków z ViewModelu

**Plik**: Wywołania `LearningScreen(...)` w nawigacji/MainActivity — dodać `onDontKnow = { viewModel.rate(Rating.DONT_KNOW) }` i `onKnowWell = { viewModel.rate(Rating.KNOW_WELL) }`.

**Cel**: Połączyć kliknięcia UI z ViewModelem.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Build APK: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- W fazie SPEAKING_SOURCE i SPEAKING_TARGET: przyciski są szare, nieaktywne
- W fazie ANSWER: przyciski mają pełny kolor (nie mute2), reagują na dotyk
- Kliknięcie "Nie wiem": tło czerwone przez ~300ms, karta się zmienia, słychać dźwięk
- Kliknięcie "Wiem!": tło zielone przez ~300ms, karta się zmienia, słychać dźwięk
- Po kilku sesjach: fiszki z różnymi srs_level w DB (sprawdzić przez API GET /collections/{id}/flashcards)

---

## Strategia testowania

### Testy jednostkowe (do dodania):

- `SrsEngineTest.kt` w `apps/frontend/shared/src/commonTest/`:
  - `intervalFor(level=0.0, DONT_KNOW)` zawsze zwraca 2
  - `intervalFor(level=0.5, KNOW)` zwraca wartość w przedziale 4–6 (base=5, jitter=1)
  - `newLevel(0.0, DONT_KNOW)` = 0.0 (nie schodzi poniżej 0)
  - `newLevel(1.0, KNOW_WELL)` = 1.0 (nie przekracza 1)
  - `initQueue` sortuje trudniejsze karty wcześniej, ale nie deterministycznie (różne ziarna RNG)
  - `pickNext` zwraca najtrudniejszą (min srsLevel) spośród zaległych

### Kroki testowania ręcznego:

1. Uruchom sesję nauki z kolekcją 5+ fiszek; zweryfikuj losową kolejność na początku
2. Naciśnij "Nie wiem" na pierwszej karcie; zweryfikuj że wraca po 2 kartach
3. Naciśnij "Wiem!" na karcie; zweryfikuj że wraca po 10+ kartach
4. Przejdź przez całą kartę bez akcji; zweryfikuj że `srs_level` wzrósł (+0.05) — przez API GET
5. Sprawdź logcat: brak błędów podczas fire & forget sync

## Uwagi dotyczące migracji

- Migracja 008 dodaje `srs_level REAL NOT NULL DEFAULT 0.0` — wszystkie istniejące fiszki startują z poziomem 0.0 (nieznane). To jest poprawne zachowanie — SRS nie ma historii dla starych danych.
- sqlx automatycznie wykona migrację przy starcie backendu (`.run(&pool).await` w `main.rs`).

## Referencje

- Badania: `context/changes/srs-learning/research.md`
- `apps/backend/src/handlers/flashcards.rs:96-130` — handler PUT
- `apps/backend/src/models.rs:49-69` — modele Rust
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:259-300` — playLoop
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/AndroidLearningController.kt` — wzorzec Intent

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku.

### Faza 1: Backend — migracja DB + PUT

#### Automatyczne

- [x] 1.1 `cargo build` bez błędów po zmianach w models.rs i handlers/flashcards.rs
- [x] 1.2 `cargo test` przechodzi

#### Ręczne

- [ ] 1.3 `PUT /flashcards/{id}` z `{"srs_level": 0.5}` zwraca 200 z `srs_level: 0.5`
- [ ] 1.4 `GET /collections/{id}/learning` zwraca fiszki z polem `srs_level`

### Faza 2: Frontend shared — model + SrsEngine + LearningController

#### Automatyczne

- [ ] 2.1 `./gradlew :shared:compileKotlinAndroid` bez błędów
- [ ] 2.2 `./gradlew :shared:test` (testy SrsEngine przechodzą)
- [ ] 2.3 `./gradlew :androidApp:assembleDebug` — APK buduje się

#### Ręczne

- [ ] 2.4 Brak regresji w ekranach zarządzania fiszkami (lista, edycja)

### Faza 3: Android — LearningService SRS

#### Automatyczne

- [ ] 3.1 `./gradlew :androidApp:assembleDebug` bez błędów

#### Ręczne

- [ ] 3.2 Sesja startuje, karty grane w losowej kolejności
- [ ] 3.3 "Nie wiem" — karta wraca po ~2 kartach, słyszalny dźwięk
- [ ] 3.4 "Wiem!" — karta wraca po 10+ kartach, słyszalny dźwięk
- [ ] 3.5 Brak KMP crashy w logcat podczas sesji

### Faza 4: UI — przyciski aktywne + feedback

#### Automatyczne

- [ ] 4.1 `./gradlew :androidApp:assembleDebug` bez błędów

#### Ręczne

- [ ] 4.2 Przyciski szare (nieaktywne) w fazach innych niż ANSWER
- [ ] 4.3 Przyciski aktywne (pełny kolor, klikalność) w fazie ANSWER
- [ ] 4.4 Kliknięcie "Nie wiem" — czerwony flash ~300ms
- [ ] 4.5 Kliknięcie "Wiem!" — zielony flash ~300ms
- [ ] 4.6 Po sesji: srs_level fiszek zaktualizowany w DB (weryfikacja przez API)
