# SRS Decay — Krzywa zapominania plan implementacji

## Przegląd

Rozszerzenie systemu SRS o krzywą zapominania Ebbinghausa. Fiszki zyskują pole `last_studied_at` (kiedy ostatnio oceniano kartę). Przy starcie sesji nauki `SrsEngine.initQueue()` stosuje decay: poziom znajomości obniżany jest proporcjonalnie do czasu, który minął od ostatniej nauki, wg formuły `level × e^(-t / stability)`, gdzie stability rośnie z poziomem (1–30 dni). Karty nieoceniane przez wiele dni wracają wcześniej w kolejce, wymuszając powtórkę.

## Analiza stanu obecnego

- `flashcards` table: brak kolumny `last_studied_at` (ostatnia migracja to 008 dodająca `srs_level`)
- `Flashcard` struct (Rust): `id, collection_id, source_text, target_text, position, created_at, srs_level`
- `FlashcardUpdateRequest` (Rust/Kotlin): ma `srs_level`, brak `last_studied_at`
- `SrsEngine.initQueue()`: używa surowego `srsLevel` z DTO — zero decay
- `LearningService.applyRating()`: wywołuje `flashcardRepo.updateSrs(id, srsLevel)` — nie wysyła timestampa
- `kotlinx-datetime` jest już w `commonMain.dependencies` (build.gradle.kts:69)
- Wszystkie SELECT w handlerach wymieniają kolumny explicite — każdy wymaga aktualizacji

## Pożądany stan końcowy

Po wdrożeniu:
- Każda fiszka ma `last_studied_at TIMESTAMP WITH TIME ZONE NULL` w DB
- `GET /collections/{id}/learning` i `GET /collections/{id}/flashcards` zwracają `last_studied_at`
- `PUT /flashcards/{id}` z `{"last_studied_at": "<iso8601>"}` zapisuje timestamp
- `FlashcardDto` ma `lastStudiedAt: String? = null`
- `SrsEngine.decayLevel(level, lastStudiedAt, now)` oblicza zdecayowany poziom
- `SrsEngine.initQueue()` inicjalizuje `SrsCard.srsLevel` zdecayowaną wartością
- Po ocenieniu karty, Android wysyła aktualny `Clock.System.now().toString()` jako `lastStudiedAt`
- Karty nieuczone przez tydzień (przy poziomie 0.8) mają poziom ~0.60 zamiast 0.80

### Kluczowe odkrycia:

- `handlers/flashcards.rs:41-43, 76-84, 104-115` — wszystkie trzy SELECT wymieniają kolumny explicite; każdy wymaga dodania `last_studied_at`
- `handlers/learning.rs:16-21` — ten sam problem z kolumnami
- `SrsEngine.kt:16-21` — `initQueue()` to właśnie miejsce na decay; `SrsCard.srsLevel` jest inicjalizowany tu, decayed value zastąpi `card.srsLevel`
- `LearningService.kt` — `applyRating()` już używa `serviceScope.launch { flashcardRepo.updateSrs(...) }` — wystarczy rozszerzyć `updateSrs` o parametr
- `kotlin.math.exp` — dostępne w commonMain bez dodatkowych importów; `kotlinx.datetime.Instant.parse()` + `Clock.System.now()` też

## Czego NIE robimy

- Nie dodajemy `last_studied_at` do `FlashcardRequest` (tworzenie fiszki) — nowe fiszki startują z `NULL`
- Nie implementujemy server-side auto-timestamp (użytkownik wybrał client-explicit)
- Nie dodajemy dolnej granicy do decay — 0.0 jest poprawną dolną wartością
- Nie zmieniamy `SrsCard.flashcard.srsLevel` — pole w DTO zostaje oryginalne; decay tylko w `SrsCard.srsLevel`
- Nie aktualizujemy `last_studied_at` przy edycji tekstu fiszki (tylko przy ocenie SRS)
- Nie migrujemy historycznych danych — istniejące fiszki mają `last_studied_at = NULL`, co oznacza brak decay (poprawne: brak historii → nie wiadomo kiedy uczone)

## Podejście do implementacji

Backend-first (migration + Rust) → shared (DTO + SrsEngine + testy) → Android (jeden punkt integracji). Każda warstwa może być weryfikowana niezależnie przed przejściem dalej.

## Krytyczne szczegóły implementacji

- **Kolejność argumentów w SQL UPDATE**: `last_studied_at` jest czwartym `COALESCE`; bindingi muszą odpowiadać pozycjom `$1…$6`. Nowy kontrakt: `$1=source_text, $2=target_text, $3=srs_level, $4=last_studied_at, $5=id, $6=user.id`
- **`decayLevel` z parametrem `now`**: funkcja musi przyjmować `now: Instant = Clock.System.now()` zamiast wywoływać `Clock.System.now()` wewnętrznie — umożliwia deterministyczne testy bez mockowania zegara
- **`exp` import**: `import kotlin.math.exp` — nie mylić z `kotlin.math.ln` (nie używamy)

---

## Faza 1: Backend + Frontend shared

### Przegląd

Dodanie `last_studied_at` do wszystkich warstw stosu: DB (migration 009), Rust models + handlery, Kotlin DTO + repository, SrsEngine z decay + testy jednostkowe.

### Wymagane zmiany:

#### 1. Migracja 009

**Plik**: `apps/backend/migrations/009_add_last_studied_at.sql`

**Cel**: Dodać kolumnę `last_studied_at` do tabeli `flashcards`.

**Kontrakt**:
```sql
ALTER TABLE flashcards
    ADD COLUMN last_studied_at TIMESTAMP WITH TIME ZONE NULL DEFAULT NULL;
```

#### 2. Rust — `Flashcard` struct

**Plik**: `apps/backend/src/models.rs:49-58`

**Cel**: Odwzorować nowe pole z DB w struct.

**Kontrakt**: Dodać `pub last_studied_at: Option<DateTime<Utc>>` po `srs_level`. Kolejność musi zgadzać się z kolejnością kolumn w RETURNING clause.

#### 3. Rust — `FlashcardUpdateRequest` struct

**Plik**: `apps/backend/src/models.rs:66-71`

**Cel**: Umożliwić klientom przekazanie `last_studied_at` w PUT.

**Kontrakt**: Dodać `pub last_studied_at: Option<DateTime<Utc>>`.

#### 4. Rust — handler `list` (GET /collections/{id}/flashcards)

**Plik**: `apps/backend/src/handlers/flashcards.rs:41-43`

**Cel**: Zwracać `last_studied_at` w liście fiszek.

**Kontrakt**: Dodać `flashcards.last_studied_at` do SELECT. Nowy SELECT: `SELECT id, collection_id, source_text, target_text, position, created_at, srs_level, last_studied_at FROM flashcards WHERE collection_id = $1 ORDER BY position`

#### 5. Rust — handler `create` (POST /collections/{id}/flashcards)

**Plik**: `apps/backend/src/handlers/flashcards.rs:76-84`

**Cel**: RETURNING z nowym polem.

**Kontrakt**: Dodać `last_studied_at` do RETURNING w INSERT.

#### 6. Rust — handler `update` (PUT /flashcards/{id})

**Plik**: `apps/backend/src/handlers/flashcards.rs:104-115`

**Cel**: Przyjmować i zapisywać `last_studied_at`; zwracać w RETURNING.

**Kontrakt** — nowy UPDATE SQL:
```sql
UPDATE flashcards SET
    source_text    = COALESCE($1, flashcards.source_text),
    target_text    = COALESCE($2, flashcards.target_text),
    srs_level      = COALESCE($3, flashcards.srs_level),
    last_studied_at = COALESCE($4, flashcards.last_studied_at)
FROM collections
WHERE flashcards.id = $5
  AND flashcards.collection_id = collections.id
  AND collections.user_id = $6
RETURNING flashcards.id, flashcards.collection_id, flashcards.source_text,
          flashcards.target_text, flashcards.position, flashcards.created_at,
          flashcards.srs_level, flashcards.last_studied_at
```
Bindingi: `$1=source_text, $2=target_text, $3=srs_level, $4=last_studied_at, $5=id, $6=user.id`. Dodać `.bind(&body.last_studied_at)` po `.bind(body.srs_level)`.

#### 7. Rust — handler `get_session` (GET /collections/{id}/learning)

**Plik**: `apps/backend/src/handlers/learning.rs:16-21`

**Cel**: Zwracać `last_studied_at` w sesji nauki.

**Kontrakt**: Dodać `f.last_studied_at` do SELECT.

#### 8. Kotlin — `FlashcardDto`

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:47-65`

**Cel**: Odwzorować `last_studied_at` z API response.

**Kontrakt**: Dodać `@SerialName("last_studied_at") val lastStudiedAt: String? = null` do `FlashcardDto`. Default `null` — kompatybilność wsteczna.

#### 9. Kotlin — `FlashcardUpdateRequest`

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt:76-86`

**Cel**: Umożliwić wysyłanie `last_studied_at` w PUT.

**Kontrakt**: Dodać `@SerialName("last_studied_at") val lastStudiedAt: String? = null`.

#### 10. Kotlin — `FlashcardRepository.updateSrs()`

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/FlashcardRepository.kt:29-33`

**Cel**: Przekazać timestamp do API.

**Kontrakt**: Zmień sygnaturę na `suspend fun updateSrs(id: String, srsLevel: Float, lastStudiedAt: String)` — wywołuje `api.updateFlashcard(id, FlashcardUpdateRequest(srsLevel = srsLevel, lastStudiedAt = lastStudiedAt))`.

#### 11. Kotlin — `SrsEngine.decayLevel()`

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngine.kt`

**Cel**: Obliczyć zdecayowany poziom znajomości wg krzywej Ebbinghausa.

**Kontrakt**:
```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.exp

fun decayLevel(
    level: Float,
    lastStudiedAt: String?,
    now: Instant = Clock.System.now(),
): Float {
    if (lastStudiedAt == null) return level
    val studied = Instant.parse(lastStudiedAt)
    val days = (now - studied).inWholeMinutes / 1440.0
    val stability = 1.0 + level * 29.0
    return (level * exp(-days / stability)).toFloat().coerceAtLeast(0f)
}
```
Używamy minut (nie dni) dla lepszej precyzji przy krótkich interwałach testowych.

#### 12. Kotlin — `SrsEngine.initQueue()` — zastosowanie decay

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngine.kt:16-21`

**Cel**: Inicjalizować `SrsCard.srsLevel` zdecayowanym poziomem zamiast surowym.

**Kontrakt**: Zmień `mapIndexed { i, card -> SrsCard(card, card.srsLevel, dueAtIndex = i) }` na `mapIndexed { i, card -> SrsCard(card, decayLevel(card.srsLevel, card.lastStudiedAt), dueAtIndex = i) }`. Dodać parametr `now: Instant = Clock.System.now()` do `initQueue` i przekazać go do `decayLevel`.

#### 13. Testy — `SrsEngineTest` — decay

**Plik**: `apps/frontend/shared/src/commonTest/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngineTest.kt`

**Cel**: Weryfikacja funkcji `decayLevel` dla kluczowych scenariuszy.

**Kontrakt** — dodać testy:
- `decayLevel(0.8f, null, now)` == `0.8f` (null → brak decay)
- `decayLevel(0.0f, anyTimestamp, now)` == `0.0f` (floor 0 nie spada poniżej 0)
- `decayLevel(0.8f, isoOf7DaysAgo, now)` ≈ `0.597f` ±0.01f (7 dni, stability=24.2)
- `decayLevel(0.8f, isoOf0DaysAgo, now)` ≈ `0.8f` ±0.001f (brak upływu czasu → brak decay)
- `decayLevel(1.0f, isoOf30DaysAgo, now)` > `0.5f` (stabilność max = 30 dni, nie może spaść o połowę w jeden czas półtrwania)

Użyć `now` jako fixed `Instant` w testach (np. `Instant.parse("2026-06-18T12:00:00Z")`); `lastStudiedAt = (now - 7.days).toString()`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Migracja stosuje się czysto: `cd apps/backend && cargo run` startuje bez błędów
- Backend build: `cargo build`
- Backend testy: `cargo test`
- Shared kompilacja: `./gradlew :shared:compileKotlinAndroid`
- Shared testy: `./gradlew :shared:test` (wszystkie testy SrsEngine przechodzą)
- Android APK: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- `GET /collections/{id}/learning` zwraca fiszki z polem `last_studied_at` (null dla starych)
- `PUT /flashcards/{id}` z `{"last_studied_at": "2026-06-01T10:00:00Z", "srs_level": 0.8}` → 200 z `last_studied_at` w odpowiedzi
- `PUT /flashcards/{id}` bez `last_studied_at` → 200, pole niezmienione

**Uwaga implementacyjna**: Po zakończeniu tej fazy, zatrzymaj się i zweryfikuj wszystkie automatyczne i ręczne kryteria przed Fazą 2.

---

## Faza 2: Android — LearningService + weryfikacja E2E

### Przegląd

Jedyna zmiana w warstwie Android: `applyRating()` w `LearningService` przekazuje `Clock.System.now().toString()` jako `lastStudiedAt` do `flashcardRepo.updateSrs()`. Następnie pełna weryfikacja działania systemu na urządzeniu.

### Wymagane zmiany:

#### 1. LearningService — `applyRating()`

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt`

**Cel**: Wysłać aktualny czas oceny do backendu jako `lastStudiedAt`.

**Kontrakt**: Zmień wywołanie `flashcardRepo.updateSrs(card.flashcard.id, card.srsLevel)` na `flashcardRepo.updateSrs(card.flashcard.id, card.srsLevel, Clock.System.now().toString())`. Dodać import `kotlinx.datetime.Clock`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- APK build: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Uruchom sesję nauki z kolekcją zawierającą fiszki z różnymi `srs_level`
- Oceń kilka kart — po sesji: `GET /collections/{id}/learning` zwraca te karty z `last_studied_at = <teraz>`
- Zamknij aplikację; poczekaj lub ustaw `last_studied_at` ręcznie na kilka dni temu przez API; uruchom nową sesję — karty z historią pojawią się wcześniej w kolejce (niższy `srsLevel` po decay = wyższy priorytet)
- Karta z `srs_level = 0.8` i `last_studied_at` sprzed 7 dni powinna mieć decay ≈ 0.60 na początku sesji
- Logcat bez błędów podczas fire & forget sync

---

## Strategia testowania

### Testy jednostkowe (SrsEngineTest.kt):

- `decayLevel(0.8f, null)` → brak decay
- `decayLevel(0.8f, 7 dni temu)` → ≈ 0.597
- `decayLevel(0.0f, ...)` → 0.0 (floor)
- `decayLevel(1.0f, 0 dni temu)` → ≈ 1.0 (brak upływu)
- `initQueue` z kartą z `lastStudiedAt` → `SrsCard.srsLevel` zdecayowany

### Testy ręczne:

1. API: `PUT /flashcards/{id}` → `last_studied_at` pojawia się w response
2. API: `GET /collections/{id}/learning` → fiszka z historią ma `last_studied_at != null`
3. Sesja nauki: karty zapomniane pojawiają się wcześniej (po ręcznym ustawieniu dawnego `last_studied_at`)
4. Fire & forget: brak błędów w logcat po ocenieniu karty

## Uwagi dotyczące migracji

Migration 009 jest addytywna — `last_studied_at NULL DEFAULT NULL` nie wpływa na istniejące dane. Wszystkie stare fiszki mają `last_studied_at = NULL`, co `decayLevel` interpretuje jako brak decay (return level). Brak data-backfill.

## Referencje

- Poprzednia implementacja SRS: `context/archive/2026-06-18-srs-learning/`
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/domain/SrsEngine.kt`
- `apps/backend/src/models.rs`
- `apps/backend/src/handlers/flashcards.rs`
- `apps/backend/src/handlers/learning.rs`
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt` — `applyRating()`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku.

### Faza 1: Backend + Frontend shared

#### Automatyczne

- [x] 1.1 `cargo build` bez błędów po zmianach w models.rs i handlerach — 3207757
- [x] 1.2 `cargo test` przechodzi — 3207757
- [x] 1.3 `./gradlew :shared:compileKotlinAndroid` bez błędów — 3207757
- [x] 1.4 `./gradlew :shared:test` — wszystkie testy SrsEngine przechodzą — 3207757
- [x] 1.5 `./gradlew :androidApp:assembleDebug` — APK buduje się — 3207757

#### Ręczne

- [x] 1.6 `GET /collections/{id}/learning` zwraca `last_studied_at` (null dla starych fiszek) — 3207757
- [x] 1.7 `PUT /flashcards/{id}` z `{"last_studied_at": "...", "srs_level": 0.8}` → 200 z polem w response — 3207757
- [x] 1.8 `PUT /flashcards/{id}` bez `last_studied_at` → 200, pole niezmienione — 3207757

### Faza 2: Android

#### Automatyczne

- [x] 2.1 `./gradlew :androidApp:assembleDebug` bez błędów — 3207757

#### Ręczne

- [x] 2.2 Po ocenieniu karty: `GET /collections/{id}/learning` pokazuje `last_studied_at = <teraz>`
- [x] 2.3 Karta z `last_studied_at` sprzed 7 dni i `srs_level = 0.8` ma zdecayowany poziom ≈ 0.60 w sesji
- [x] 2.4 Logcat bez błędów podczas fire & forget sync
