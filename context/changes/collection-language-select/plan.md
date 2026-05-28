# Wybór języka kolekcji — Plan implementacji

## Przegląd

Dodanie pól `source_language` i `target_language` do kolekcji: migracja bazy danych, aktualizacja modeli Rust i Kotlin, nowy komponent `LangSelect` (chip + ModalBottomSheet), dwa nowe pola w `CollectionFormScreen` oraz dynamiczne wyświetlanie flag w `FlashcardsScreen`.

## Analiza stanu obecnego

- `collections` tabela: `id, user_id, name, created_at` — brak pól językowych
- `Collection` struct i `CollectionRequest` (Rust): brak pól językowych
- `CollectionDto` i `CollectionRequest` (Kotlin): brak pól językowych
- `CollectionFormScreen`: pola NAZWA i OPIS — brak selektora języka
- `FlashcardsScreen`: hardcoded `"PL → EN"`, `Flag("pl")`, `Flag("en")`, `CapsLabel("POLSKI → ANGIELSKI")`
- `Flag.kt` ma `LanguageNames` mapę z 6 językami: `pl, en, de, es, fr, it`
- `.tmp/UI/_TODO.kt` definiuje zamierzony UX: `LangSelect` chip + ModalBottomSheet

## Pożądany stan końcowy

1. Nowa migracja dodaje `source_language TEXT NOT NULL DEFAULT 'pl'` i `target_language TEXT NOT NULL DEFAULT 'en'` do `collections`
2. Backend waliduje: kod języka musi być jednym z `{pl, en, de, es, fr, it}`, source ≠ target
3. `CollectionDto` niesie `sourceLanguage` i `targetLanguage`; `CollectionRequest` je wysyła
4. `CollectionFormScreen` pokazuje dwa chipy LangSelect (J. OJCZYSTY, J. DO NAUKI); domyślnie `pl → en`; CTA disabled gdy source == target
5. `FlashcardsScreen` używa flag i nazw z kolekcji zamiast hardcoded PL/EN
6. `./gradlew :androidApp:assembleDebug` i `cargo build` bez błędów

## Kluczowe odkrycia

- `LanguageNames` (Flag.kt:99) już istnieje — reużyć w LangSelect zamiast definiować ponownie
- `ApiClient` używa `Json { ignoreUnknownKeys = true }` — frontend bezpiecznie zignoruje nowe pola backendowe, jeśli deployment nie jest zsynchronizowany
- `CollectionsScreen.onEditClick: (String, String) -> Unit` przekazuje tylko `(id, name)` — potrzebna zmiana na `(CollectionDto)` żeby przekazać języki do ekranu edycji
- `ModalBottomSheet` jest już używany w `CollectionFormScreen` (import + `@OptIn` już obecne) — LangSelect może go reużyć

## Czego NIE robimy

- Nie zmieniamy tabeli `flashcards` (kolumny `polish_text`, `english_text` zostają)
- Nie walidujemy poprawności kodu języka w bazie (TEXT, nie PostgreSQL ENUM)
- Nie dodajemy języka do `CollectionsScreen` LaneRow (brak miejsca w layoucie)
- Nie implementujemy dynamicznego tłumaczenia (`TranslateService` nadal disabled)

---

## Phase 1: Backend — migracja + model + handlery

### Changes Required

#### 1. Migracja SQL

**Plik**: `backend/migrations/003_add_languages.sql`

**Cel**: Dodać kolumny językowe do istniejącej tabeli z bezpiecznymi wartościami domyślnymi.

**Kontrakt**:
```sql
ALTER TABLE collections
    ADD COLUMN source_language TEXT NOT NULL DEFAULT 'pl',
    ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en';
```

#### 2. Model Collection (Rust)

**Plik**: `backend/src/models.rs`

**Cel**: Rozszerzyć struct `Collection` o nowe pola oraz `CollectionRequest` o parametry języka.

**Kontrakt**:
```rust
pub struct Collection {
    pub id: Uuid,
    pub user_id: Uuid,
    pub name: String,
    pub source_language: String,
    pub target_language: String,
    pub created_at: DateTime<Utc>,
}

pub struct CollectionRequest {
    pub name: String,
    pub source_language: String,
    pub target_language: String,
}
```

#### 3. Handlery kolekcji

**Plik**: `backend/src/handlers/collections.rs`

**Cel**: Zaktualizować zapytania SQL (INSERT/UPDATE/SELECT) oraz dodać walidację języka.

**Kontrakt** — dodać przed handlerami create/update:
```rust
const VALID_LANGUAGES: &[&str] = &["pl", "en", "de", "es", "fr", "it"];

fn validate_languages(src: &str, tgt: &str) -> bool {
    VALID_LANGUAGES.contains(&src)
        && VALID_LANGUAGES.contains(&tgt)
        && src != tgt
}
```

Jeśli walidacja nie przejdzie → `HttpResponse::UnprocessableEntity()`.

Zaktualizowane zapytania:
- GET LIST: `SELECT id, user_id, name, source_language, target_language, created_at FROM collections WHERE user_id = $1 ORDER BY created_at DESC`
- POST INSERT: `INSERT INTO collections (user_id, name, source_language, target_language) VALUES ($1, $2, $3, $4) RETURNING id, user_id, name, source_language, target_language, created_at`; parametry: `user_id, req.name, req.source_language, req.target_language`
- PUT UPDATE: `UPDATE collections SET name = $1, source_language = $2, target_language = $3 WHERE id = $4 AND user_id = $5 RETURNING id, user_id, name, source_language, target_language, created_at`; parametry: `req.name, req.source_language, req.target_language, collection_id, user_id`

### Success Criteria

#### Automated Verification

- `cargo build` przechodzi bez błędów (modele, handlery, migracja parsuje się)

#### Manual Verification

- `cargo run` + `curl POST /collections` z `{"name":"Test","source_language":"pl","target_language":"en"}` → 201 z polami językowymi
- `curl GET /collections` → kolekcje z `source_language` i `target_language`
- `curl POST /collections` z `{"name":"Bad","source_language":"pl","target_language":"pl"}` → 422

---

## Phase 2: Frontend — modele danych + repozytorium + VM

### Changes Required

#### 1. ApiModels.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt`

**Cel**: Dodać pola językowe do `CollectionDto` (deserializacja odpowiedzi) i `CollectionRequest` (serializacja żądania).

**Kontrakt**:
```kotlin
data class CollectionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("source_language") val sourceLanguage: String,
    @SerialName("target_language") val targetLanguage: String,
    @SerialName("created_at") val createdAt: String,
)

data class CollectionRequest(
    val name: String,
    @SerialName("source_language") val sourceLanguage: String,
    @SerialName("target_language") val targetLanguage: String,
)
```

#### 2. CollectionRepository.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/CollectionRepository.kt`

**Cel**: Przekazać parametry języka do `CollectionRequest` w metodach `create` i `rename`.

**Kontrakt** — nowe sygnatury:
- `suspend fun create(name: String, sourceLanguage: String, targetLanguage: String): Result<CollectionDto>`
  — body: `CollectionRequest(name, sourceLanguage, targetLanguage)`
- `suspend fun rename(id: String, name: String, sourceLanguage: String, targetLanguage: String): Result<CollectionDto>`
  — body: `CollectionRequest(name, sourceLanguage, targetLanguage)`

#### 3. CollectionsViewModel.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsViewModel.kt`

**Cel**: Rozszerzyć sygnatury `createCollection` i `updateCollection` o parametry języka.

**Kontrakt** — nowe sygnatury:
- `fun createCollection(name: String, sourceLanguage: String, targetLanguage: String)`
  — wywołuje `repo.create(name, sourceLanguage, targetLanguage)`
- `fun updateCollection(id: String, name: String, sourceLanguage: String, targetLanguage: String)`
  — wywołuje `repo.rename(id, name, sourceLanguage, targetLanguage)`

### Success Criteria

#### Automated Verification

- `./gradlew :androidApp:assembleDebug` przechodzi bez błędów kompilacji

#### Manual Verification

- Kompilacja bez błędów IDE w ApiModels.kt, CollectionRepository.kt, CollectionsViewModel.kt

---

## Phase 3: Frontend UI — LangSelect + ekrany

### Changes Required

#### 1. Nowy komponent LangSelect

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/LangSelect.kt`

**Cel**: Chip-style selektor języka z ModalBottomSheet listą wszystkich obsługiwanych języków.

**Kontrakt** — sygnatura:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LangSelect(
    code: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout chipu: `Row` z `Flag(code, 22.dp)` + `Spacer(4.dp)` + `Text(LanguageNames[code] ?: code)` + `Icon(ChevronRight, 16.dp)`, opakowany w `Box` ze `background(c.surface2)`, `border(1.dp, c.line)`, `clip(RoundedCornerShape(12.dp))`, `padding(horizontal=12.dp, vertical=8.dp)`, `clickable { showSheet = true }`.

ModalBottomSheet — gdy `showSheet`:
- Nagłówek: `CapsLabel("WYBIERZ JĘZYK")` + `Spacer(12.dp)`
- Lista: `LanguageNames.entries` (zachować kolejność: pl, en, de, es, fr, it) — dla każdego wpisu Row z `Flag(code, 24.dp)` + `Text(name, bodyLarge)` + `Spacer(weight=1f)` + jeśli `code == currentCode` to `Icon(Check, tint=accent)`. Cały Row `clickable { onSelect(code); showSheet = false }`
- `Spacer(Modifier.height(32.dp))` na dole (bezpieczny obszar)

#### 2. CollectionFormScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionFormScreen.kt`

**Cel**: Dodać parametry języka do sygnatury, dwa pola LangSelect do formularza, walidację source ≠ target.

**Kontrakt** — rozszerzona sygnatura:
```kotlin
fun CollectionFormScreen(
    collectionId: String? = null,
    collectionName: String = "",
    sourceLanguage: String = "pl",
    targetLanguage: String = "en",
    viewModel: CollectionsViewModel = koinViewModel(),
    onBack: () -> Unit,
)
```

Stan: dwa nowe `var`:
- `var sourceLang by remember { mutableStateOf(sourceLanguage) }`
- `var targetLang by remember { mutableStateOf(targetLanguage) }`

Walidacja CTA: `val isValid = name.isNotBlank() && sourceLang != targetLang` (zastąpić `name.isNotBlank()` w warunkach enabled).

Dwa nowe pola za sekcją OPIS (wewnątrz `Column` formularza):
```kotlin
Spacer(Modifier.height(16.dp))
CapsLabel("J. OJCZYSTY")
Spacer(Modifier.height(6.dp))
LangSelect(code = sourceLang, onSelect = { sourceLang = it }, modifier = Modifier.fillMaxWidth())

Spacer(Modifier.height(16.dp))
CapsLabel("J. DO NAUKI")
Spacer(Modifier.height(6.dp))
LangSelect(code = targetLang, onSelect = { targetLang = it }, modifier = Modifier.fillMaxWidth())
```

Wywołania VM (zastąpić istniejące):
- `viewModel.updateCollection(collectionId!!, name.trim(), sourceLang, targetLang)`
- `viewModel.createCollection(name.trim(), sourceLang, targetLang)`

Dodać import: `pl.rkarpinski.fiszkiwbiegu.ui.components.LangSelect`

#### 3. FlashcardsScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/FlashcardsScreen.kt`

**Cel**: Zastąpić hardcoded `"PL → EN"`, `Flag("pl")`, `Flag("en")` i `CapsLabel("POLSKI → ANGIELSKI")` danymi z `collection.sourceLanguage` i `collection.targetLanguage`.

**Kontrakt** — trzy lokalizacje:
1. Hero text (linia ~138): `Text("PL → EN", ...)` → `Text("${collection.sourceLanguage.uppercase()} → ${collection.targetLanguage.uppercase()}", ...)`
2. Language pair row (linie ~212-223):
   - `Flag("pl", 26.dp)` → `Flag(collection.sourceLanguage, 26.dp)`
   - `Flag("en", 26.dp)` → `Flag(collection.targetLanguage, 26.dp)`
   - `CapsLabel("POLSKI → ANGIELSKI")` → `CapsLabel("${LanguageNames[collection.sourceLanguage]?.uppercase() ?: collection.sourceLanguage.uppercase()} → ${LanguageNames[collection.targetLanguage]?.uppercase() ?: collection.targetLanguage.uppercase()}")`

Dodać import: `pl.rkarpinski.fiszkiwbiegu.ui.components.LanguageNames`

#### 4. CollectionsScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Zmienić sygnaturę `onEditClick` z `(String, String)` na `(CollectionDto)` żeby przekazać języki kolekcji do ekranu edycji.

**Kontrakt**:
- Parametr: `onEditClick: (CollectionDto) -> Unit = {}`
- W `LaneRow`: `onEditClick = { onEditClick(collection) }`
- Dodać import `pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto` jeśli brakuje

#### 5. App.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Rozszerzyć `Route.CollectionForm` o pola językowe i zaktualizować lambda w `entry<Route.Collections>` oraz `entry<Route.CollectionForm>`.

**Kontrakt**:

`Route.CollectionForm`:
```kotlin
data class CollectionForm(
    val collectionId: String? = null,
    val collectionName: String = "",
    val sourceLanguage: String = "pl",
    val targetLanguage: String = "en",
) : Route
```

`entry<Route.Collections>` — zmienić `onEditClick`:
```kotlin
onEditClick = { collection ->
    backStack.add(Route.CollectionForm(collection.id, collection.name, collection.sourceLanguage, collection.targetLanguage))
},
```

`entry<Route.CollectionForm>` — przekazać języki:
```kotlin
entry<Route.CollectionForm> { route ->
    CollectionFormScreen(
        collectionId = route.collectionId,
        collectionName = route.collectionName,
        sourceLanguage = route.sourceLanguage,
        targetLanguage = route.targetLanguage,
        onBack = { backStack.removeLastOrNull() },
    )
}
```

### Success Criteria

#### Automated Verification

- `./gradlew :androidApp:assembleDebug` bez błędów

#### Manual Verification

- FAB CollectionsScreen → formularz nowej kolekcji pokazuje J. OJCZYSTY = pl, J. DO NAUKI = en
- Kliknięcie chipu J. OJCZYSTY otwiera bottom-sheet z listą 6 języków
- Wybranie pl jako obu języków → CTA disabled
- Wybranie pl → de → zapis → kolekcja tworzy się z pl/de
- Edycja kolekcji → formularz otwiera się z językami zapisanymi przy tworzeniu
- FlashcardsScreen dla kolekcji pl→de pokazuje flagi PL i DE, napis "PL → DE"
- Kolekcja pl→en (istniejąca) nadal wyświetla PL → EN w FlashcardsScreen

---

## Progress

### Phase 1: Backend — migracja + model + handlery

#### Automated
- [x] 1.1 cargo build bez błędów

#### Manual
- [x] 1.2 POST /collections z językami → 201
- [x] 1.3 GET /collections → pola source/target_language obecne
- [x] 1.4 POST /collections source == target → 422

### Phase 2: Frontend — modele danych + repozytorium + VM

#### Automated
- [x] 2.1 :androidApp:assembleDebug bez błędów kompilacji — 8db268f

#### Manual
- [x] 2.2 Brak błędów IDE w ApiModels.kt, CollectionRepository.kt, CollectionsViewModel.kt — 8db268f

### Phase 3: Frontend UI — LangSelect + ekrany

#### Automated
- [x] 3.1 :androidApp:assembleDebug bez błędów

#### Manual
- [x] 3.2 Nowa kolekcja — LangSelect pokazuje pl/en domyślnie, bottom-sheet z 6 językami
- [x] 3.3 CTA disabled gdy source == target
- [x] 3.4 Tworzenie kolekcji z niestandardową parą języków
- [x] 3.5 Edycja kolekcji — języki wczytane z kolekcji
- [x] 3.6 FlashcardsScreen — dynamiczne flagi i nazwy języków
