---
change_id: ui-reskin-backend-stubs
title: Backend dla zaślepek UI — auth/me, lastStudied, progress
status: planned
created: 2026-05-29
updated: 2026-05-29
---

## Overview

Zastąpienie trzech aktywnych zaślepek UI realnymi danymi:

| Zaślepka | Plik | Linia |
|----------|------|-------|
| `"Ty"` — stub displayName | `ProfileScreen.kt` | 82 |
| `progress = 0f` — TrackBar | `CollectionsScreen.kt` | 214 |
| `"0%"` — stat fiszek | `FlashcardsScreen.kt` | 152 |

TranslateService pozostaje zaślepką — poza zakresem tego planu.

**Trzy cechy end-to-end:**
1. **auth/me** — nowy endpoint `GET /auth/me` zwracający `{id, email, display_name, streak_days}`; `display_name` pobierane z claimu `name` tokena Google przy logowaniu.
2. **lastStudied** — kolumna `collections.last_studied TIMESTAMPTZ NULL`; aktualizowana przez `POST /collections/{id}/learning/complete` po sesji nauki.
3. **progress** — kolumna `collections.progress FLOAT NOT NULL DEFAULT 0`; aktualizowana tym samym endpointem (`cards_heard / total_cards`); zwracana przy `GET /collections`.

## What We're NOT Doing

- TranslateService (ML Kit) — oddzielny change-id
- Apple / Facebook Sign-In — poza MVP scope
- streak_days logika (serie treningowe) — kolumna dodana z DEFAULT 0, logika inkrementacji w przyszłości
- Paginacja / cache po stronie frontendu — bez zmian

---

## Phase 1: DB Migrations

**Wynik:** dwie nowe migracje uruchamiają się bez błędów przy starcie backendu; nowe kolumny widoczne w DB.

### Changes Required

- **`backend/migrations/005_add_user_profile.sql`** (NOWY)
  ```sql
  ALTER TABLE users ADD COLUMN display_name TEXT;
  ALTER TABLE users ADD COLUMN streak_days INTEGER NOT NULL DEFAULT 0;
  ```

- **`backend/migrations/006_add_collection_tracking.sql`** (NOWY)
  ```sql
  ALTER TABLE collections ADD COLUMN last_studied TIMESTAMPTZ;
  ALTER TABLE collections ADD COLUMN progress FLOAT NOT NULL DEFAULT 0;
  ```

### Automated Verification

```bash
cd backend && cargo build 2>&1 | tail -5
```

### Manual Verification

- [ ] `cargo run` startuje bez błędów migracji
- [ ] `psql $DATABASE_URL -c "\d users"` pokazuje kolumny `display_name` i `streak_days`
- [ ] `psql $DATABASE_URL -c "\d collections"` pokazuje kolumny `last_studied` i `progress`

---

## Phase 2: Backend — modele, handlery, routing

**Wynik:** `GET /auth/me` zwraca profil; `POST /collections/{id}/learning/complete` aktualizuje `last_studied` i `progress`; `GET /collections` zwraca nowe pola.

### Changes Required

- **`backend/src/models.rs`**
  - `User` struct: dodaj `display_name: Option<String>`, `streak_days: i32`
  - `Collection` struct: dodaj `last_studied: Option<DateTime<Utc>>`, `progress: f32`
  - Nowy `LearningCompleteRequest`:
    ```rust
    #[derive(Debug, Deserialize)]
    pub struct LearningCompleteRequest {
        pub cards_heard: i32,
        pub total_cards: i32,
    }
    ```

- **`backend/src/handlers/auth.rs`**
  - `GoogleTokenInfo`: dodaj `name: Option<String>`
  - `login()`: rozszerz INSERT o `display_name`:
    ```sql
    INSERT INTO users (google_id, email, display_name) VALUES ($1, $2, $3)
    ON CONFLICT (google_id)
      DO UPDATE SET email = EXCLUDED.email, display_name = EXCLUDED.display_name
    RETURNING id, google_id, email, display_name, streak_days, created_at
    ```
    `.bind(&token_info.name)` jako trzeci parametr
  - Nowa funkcja `me(pool, user: AuthUser)`:
    ```rust
    sqlx::query_as::<_, User>("SELECT ... FROM users WHERE id = $1")
    // zwraca json!({ "id": u.id, "email": u.email, "display_name": u.display_name, "streak_days": u.streak_days })
    ```

- **`backend/src/handlers/collections.rs`**
  - `list()`: zaktualizuj zapytanie SELECT o nowe kolumny (`last_studied`, `progress`)
  - `create()`: zaktualizuj RETURNING o nowe kolumny
  - `update()`: zaktualizuj RETURNING o nowe kolumny
  - Nowa funkcja `learning_complete(pool, user, path, body: LearningCompleteRequest)`:
    ```rust
    let progress = if body.total_cards > 0 {
        (body.cards_heard as f32 / body.total_cards as f32).clamp(0.0, 1.0)
    } else { 0.0 };
    // UPDATE collections SET last_studied = NOW(), progress = $1
    // WHERE id = $2 AND user_id = $3
    // zwraca 204 No Content
    ```

- **`backend/src/main.rs`**
  - Dodaj `GET /auth/me`:
    ```rust
    web::scope("/auth")
        .route("/login", web::post().to(handlers::auth::login))
        .route("/me", web::get().to(handlers::auth::me))
    ```
  - Dodaj `POST /collections/{id}/learning/complete`:
    ```rust
    .route("/{id}/learning/complete", web::post().to(handlers::collections::learning_complete))
    ```

### Automated Verification

```bash
cd backend && cargo test 2>&1 | tail -20
```

### Manual Verification

- [ ] `cargo run` startuje bez błędów kompilacji
- [ ] `curl -H "Authorization: Bearer <token>" https://localhost:8080/auth/me` zwraca JSON z `display_name`
- [ ] `curl -H "Authorization: Bearer <token>" https://localhost:8080/collections` zawiera pola `last_studied` (null) i `progress` (0)
- [ ] `curl -X POST -H "Authorization: Bearer <token>" -d '{"cards_heard":3,"total_cards":5}' .../collections/<id>/learning/complete` zwraca 204

---

## Phase 3: Frontend — DTOs, ViewModel, UI

**Wynik:** ProfileScreen pokazuje displayName i email; TrackBar i stat "POSTĘP" używają realnych danych z API; po sesji nauki postęp się aktualizuje.

### Changes Required

- **`frontend/shared/.../data/api/ApiModels.kt`**
  - `CollectionDto`: dodaj `@SerialName("last_studied") val lastStudied: String? = null` i `val progress: Float = 0f`
  - Nowy `UserDto`:
    ```kotlin
    @Serializable
    data class UserDto(
        val id: String,
        val email: String,
        @SerialName("display_name") val displayName: String?,
        @SerialName("streak_days") val streakDays: Int,
    )
    ```
  - Nowy `LearningCompleteRequest`:
    ```kotlin
    @Serializable
    data class LearningCompleteRequest(
        @SerialName("cards_heard") val cardsHeard: Int,
        @SerialName("total_cards") val totalCards: Int,
    )
    ```

- **`frontend/shared/.../data/api/ApiClient.kt`**
  - Dodaj `getMe()`:
    ```kotlin
    suspend fun getMe(): HttpResponse =
        client.get("$API_BASE_URL/auth/me") { bearerAuth(requireToken()) }
    ```
  - Dodaj `patchLearningComplete(collectionId, cardsHeard, totalCards)`:
    ```kotlin
    suspend fun patchLearningComplete(collectionId: String, cardsHeard: Int, totalCards: Int): HttpResponse =
        client.post("$API_BASE_URL/collections/$collectionId/learning/complete") {
            bearerAuth(requireToken())
            contentType(ContentType.Application.Json)
            setBody(LearningCompleteRequest(cardsHeard, totalCards))
        }
    ```

- **`frontend/shared/.../data/repository/CollectionRepository.kt`**
  - Dodaj `markStudied(id, cardsHeard, totalCards)`:
    ```kotlin
    suspend fun markStudied(id: String, cardsHeard: Int, totalCards: Int): Result<Unit> = runCatching {
        val response = api.patchLearningComplete(id, cardsHeard, totalCards)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }
    ```

- **NOWY `frontend/shared/.../data/repository/ProfileRepository.kt`**
  ```kotlin
  class ProfileRepository(private val api: ApiClient) {
      suspend fun getMe(): Result<UserDto> = runCatching {
          val response = api.getMe()
          if (response.status.isSuccess()) response.body()
          else error("HTTP ${response.status.value}")
      }
  }
  ```

- **NOWY `frontend/shared/.../ProfileViewModel.kt`**
  ```kotlin
  data class ProfileUiState(
      val displayName: String = "",
      val email: String = "",
      val streakDays: Int = 0,
      val isLoading: Boolean = false,
      val error: String? = null,
  )
  class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {
      private val _uiState = MutableStateFlow(ProfileUiState())
      val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
      init { loadProfile() }
      private fun loadProfile() { /* GET /auth/me → uiState */ }
  }
  ```

- **`frontend/shared/.../di/AppModule.kt`**
  - Dodaj `single { ProfileRepository(get()) }`
  - Dodaj `viewModel { ProfileViewModel(get()) }`

- **`frontend/shared/.../ProfileScreen.kt`**
  - Nowy parametr: `viewModel: ProfileViewModel = koinViewModel()`
  - `val uiState by viewModel.uiState.collectAsState()`
  - Avatar initial: `uiState.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"`
  - Nazwa: `Text(uiState.displayName.ifBlank { "…" }, ...)`
  - Email: nowy wiersz w karcie profilu pod nazwą — `Text(uiState.email, ...)`

- **`frontend/shared/.../CollectionsScreen.kt`**
  - Zmień `TrackBar(progress = 0f, ...)` → `TrackBar(progress = collection.progress, ...)`

- **`frontend/shared/.../FlashcardsScreen.kt`**
  - Zmień `StatTile(label = "POSTĘP", value = "0%", ...)` → `StatTile(label = "POSTĘP", value = "${(collection.progress * 100).toInt()}%", ...)`

- **`frontend/shared/.../LearningViewModel.kt`**
  - Dodaj `collectionRepo: CollectionRepository` jako parametr konstruktora
  - Zmień `stop()`:
    ```kotlin
    fun stop() {
        val s = controller.state.value
        if (s.flashcards.isNotEmpty()) {
            viewModelScope.launch {
                collectionRepo.markStudied(
                    collectionId,
                    cardsHeard = minOf(s.currentIndex + 1, s.flashcards.size),
                    totalCards = s.flashcards.size,
                )
            }
        }
        controller.stop()
    }
    ```
  - Zaktualizuj DI w `AppModule.kt`: `viewModel { params -> LearningViewModel(get(), get(), get(), params.get()) }`

### Automated Verification

```bash
cd frontend && ./gradlew :androidApp:assembleDebug 2>&1 | tail -20
```

### Manual Verification

- [ ] ProfileScreen wyświetla displayName z Google (nie "Ty") i email poniżej
- [ ] CollectionsScreen — TrackBar kolekcji = 0% przed sesją nauki
- [ ] Po ukończeniu sesji nauki i powrocie do kolekcji — TrackBar i stat POSTĘP pokazują wartość > 0%
- [ ] FlashcardsScreen — stat POSTĘP = "{N}%" (pobrane z CollectionDto.progress)

---

## Progress

### Phase 1: DB Migrations
#### Automated
- [x] 1.1 Utwórz backend/migrations/005_add_user_profile.sql — b077af7
- [x] 1.2 Utwórz backend/migrations/006_add_collection_tracking.sql — b077af7
- [x] 1.3 cargo build — migracje kompilują się — b077af7

#### Manual
- [x] 1.4 cargo run — brak błędów migracji, kolumny widoczne w DB — b077af7

### Phase 2: Backend — modele, handlery, routing
#### Automated
- [x] 2.1 models.rs — User: dodaj display_name, streak_days
- [x] 2.2 models.rs — Collection: dodaj last_studied, progress
- [x] 2.3 models.rs — dodaj LearningCompleteRequest
- [x] 2.4 handlers/auth.rs — GoogleTokenInfo: dodaj name; login: zapisz display_name; dodaj me()
- [x] 2.5 handlers/collections.rs — zaktualizuj list/create/update RETURNING; dodaj learning_complete()
- [x] 2.6 main.rs — zarejestruj GET /auth/me i POST /collections/{id}/learning/complete
- [x] 2.7 cargo test — wszystkie testy przechodzą

#### Manual
- [x] 2.8 GET /auth/me zwraca display_name
- [x] 2.9 GET /collections zawiera last_studied i progress
- [x] 2.10 POST .../learning/complete zwraca 204

### Phase 3: Frontend — DTOs, ViewModel, UI
#### Automated
- [x] 3.1 ApiModels.kt — zaktualizuj CollectionDto; dodaj UserDto, LearningCompleteRequest — eae3612
- [x] 3.2 ApiClient.kt — dodaj getMe(), patchLearningComplete() — eae3612
- [x] 3.3 CollectionRepository.kt — dodaj markStudied() — eae3612
- [x] 3.4 Utwórz ProfileRepository.kt — eae3612
- [x] 3.5 Utwórz ProfileViewModel.kt — eae3612
- [x] 3.6 AppModule.kt — zarejestruj ProfileRepository, ProfileViewModel; zaktualizuj LearningViewModel DI — eae3612
- [x] 3.7 ProfileScreen.kt — podepnij ProfileViewModel; pokaż displayName, email — eae3612
- [x] 3.8 CollectionsScreen.kt — TrackBar: progress = collection.progress — eae3612
- [x] 3.9 FlashcardsScreen.kt — POSTĘP stat z collection.progress — eae3612
- [x] 3.10 LearningViewModel.kt — stop() wywołuje markStudied; dodaj collectionRepo param — eae3612
- [x] 3.11 ./gradlew :androidApp:assembleDebug — kompiluje się bez błędów — eae3612

#### Manual
- [x] 3.12 ProfileScreen: displayName z Google + email widoczne
- [x] 3.13 Po sesji nauki: TrackBar i POSTĘP aktualizują się
