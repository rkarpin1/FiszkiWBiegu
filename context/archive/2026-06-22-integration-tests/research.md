---
date: 2026-06-22T17:40:37+0000
researcher: Robert Karpiński
git_commit: 22e060e7e7ee081fb1d45451c22e935f35a088a9
branch: MVP
repository: FiszkiWBiegu
topic: "Testy integracyjne backendu (Docker Postgres + REST, weryfikacja w DB)"
tags: [research, codebase, backend, integration-tests, actix-web, sqlx, testcontainers]
status: complete
last_updated: 2026-06-22
last_updated_by: Robert Karpiński
---

# Research: Testy integracyjne backendu (Docker Postgres + REST)

**Date**: 2026-06-22T17:40:37+0000
**Researcher**: Robert Karpiński
**Git Commit**: 22e060e7e7ee081fb1d45451c22e935f35a088a9
**Branch**: MVP
**Repository**: FiszkiWBiegu

## Research Question

Napisać testy integracyjne dla backendu (`apps/backend/tests/`). Postgres oparty na Dockerze (image już zainstalowany, uśpiony). Przetestować **wszystkie endpointy**. Użytkownik może być wstawiany bezpośrednio do DB, ale pozostała funkcjonalność musi być testowana **via REST**, a wynik **porównywany w DB**. Dla każdego endpointu kilka testów, w tym na danych błędnych/niepełnych. Krytyczny dobór testów. Każdy etap komentowany po angielsku.

**Decyzje zakresu (potwierdzone z użytkownikiem):**
1. **Harness**: spawn realnego serwera + klient HTTP (`reqwest`); asercje przez osobny pool `sqlx` do DB.
2. **Docker**: crate `testcontainers` (auto start/stop `postgres:16-alpine`).
3. **`/auth/login`**: pominięty całkowicie; user wstawiany do DB + JWT generowany w teście.

## Summary

- Backend wystawia **14 tras** (`apps/backend/src/main.rs:116-142`); aplikacyjnych endpointów do przetestowania jest **11** (collections ×5, flashcards ×4, learning ×1, `/info`). Pomijamy `/auth/login` (zewnętrzny Google) i happy-path `/deploy` (podmienia binarkę).
- **Uwierzytelnianie testów jest proste**: `AuthUser` dekoduje JWT HS256 sekretem `JWT_SECRET` (`apps/backend/src/auth.rs:65-75`). Test wstawia usera do DB i sam generuje JWT (`Claims { sub: user_id, exp }`) — nie trzeba dotykać Google.
- **Największe tarcie**: crate jest **binarką bez `lib.rs`**, a konfiguracja `App` siedzi **inline** w `HttpServer::new(...)` (`main.rs:105-143`). Testy w `tests/` nie mają dostępu do `create_jwt` ani budowy routingu. Rekomendacja: wydzielić cienki `src/lib.rs` (factory `build_app` + `run_migrations` + reeksport `create_jwt`) i startować serwer in-process na porcie `0`.
- **Migracje** stosują się przez `sqlx::migrate!("./migrations")` (`main.rs:95-98`) — wbudowane w binarkę w czasie kompilacji; testcontainers daje świeżą bazę, na której uruchamiamy te same migracje.
- **Schemat po migracjach 001–010** jest w pełni odtworzony (sekcja niżej) — brak CHECK na językach/SRS/progress, więc walidacja językowa to wyłącznie logika handlera (`collections.rs:9-13`).
- **Krytyczne luki walidacji i niespójności** (idealne cele testów negatywnych): brak walidacji pustego tekstu fiszki, brak ograniczeń `progress`/`session_minutes`, `GET .../learning` zwraca `200 []` zamiast `404` dla cudzej kolekcji, `PUT /collections/{id}` zwraca `flashcard_count = 0` (bug).

## Detailed Findings

### A. Katalog endpointów (cel testów)

Trasy: `apps/backend/src/main.rs:116-142`. Handlery aplikacyjne używają **inline `HttpResponse`** z ciałem `{"error": "..."}`; jedynie `/deploy` używa `AppError` (`apps/backend/src/error.rs:24-34`). Odrzucenia ekstraktorów actix (zły JSON, zły UUID) zwracają `400` jako **plain text**, nie JSON.

| # | Endpoint | Sukces | Walidacja → kod | Ownership | Plik |
|---|----------|--------|------------------|-----------|------|
| 1 | `GET /info` | 200 text `"{name} {version}"` | — | brak auth | `main.rs:37-44` |
| 2 | `GET /collections` | 200 `[Collection]` | — | `WHERE user_id=$1` | `collections.rs:15-33` |
| 3 | `POST /collections` | 201 `Collection` | blank name → **422**; złe/identyczne języki → **422**; brak pola → 400 | własny user | `collections.rs:35-70` |
| 4 | `PUT /collections/{id}` | 200 `Collection` | jak #3; brak → **404** | `WHERE id=$ AND user_id=$` | `collections.rs:72-111` |
| 5 | `DELETE /collections/{id}` | 204 | brak/cudza → **404** | `rows_affected==0`→404 | `collections.rs:113-135` |
| 6 | `GET /collections/{id}/flashcards` | 200 `[Flashcard]` | cudza/brak → **404** | `verify_collection_owner` | `flashcards.rs:23-55` |
| 7 | `POST /collections/{id}/flashcards` | 201 `Flashcard` | ⚠️ **brak walidacji tekstu**; cudza → 404 | `verify_collection_owner` | `flashcards.rs:57-94` |
| 8 | `GET /collections/{id}/learning` | 200 `[Flashcard]` | ⚠️ cudza/brak → **200 `[]`** (nie 404!) | JOIN `c.user_id=$2` | `learning.rs:9-35` |
| 9 | `POST /collections/{id}/learning/complete` | 204 | ⚠️ **brak bounds**; cudza → 404 | `rows_affected==0`→404 | `collections.rs:137-168` |
| 10 | `PUT /flashcards/{id}` | 200 `Flashcard` | ⚠️ brak walidacji; cudza → **404** | JOIN `collections.user_id=$6` | `flashcards.rs:96-135` |
| 11 | `DELETE /flashcards/{id}` | 204 | brak/cudza → **404** | `USING collections ... user_id=$2` | `flashcards.rs:137-166` |
| — | `POST /deploy` | 200 (pomijamy) | 503/401/400 (testujemy odrzucenia) | `X-Deploy-Key` | `deploy.rs:7-69` |

**Modele żądań** (`apps/backend/src/models.rs`):
- `CollectionRequest` (`:36-42`): `name, description, source_language, target_language` — wszystkie wymagane (brak `Option`).
- `FlashcardRequest` (`:62-66`): `source_text, target_text` — wymagane.
- `FlashcardUpdateRequest` (`:68-74`): `source_text?, target_text?, srs_level?, last_studied_at?` — wszystkie opcjonalne (partial update przez `COALESCE`).
- `LearningCompleteRequest` (`:44-48`): `progress: f32, session_minutes: i32` — wymagane.

**Walidacja języków** (`collections.rs:9-13`): dozwolone `["pl","en","de","es","fr","it"]` **oraz** `source != target`; inaczej **422** `{"error":"Invalid or identical language codes"}`. Blank name → **422** `{"error":"Name must not be blank"}` (`collections.rs:40-43`).

### B. Schemat bazy po migracjach 001–010

```sql
users (
  id           UUID PK DEFAULT gen_random_uuid(),   -- 002:2
  google_id    TEXT UNIQUE NOT NULL,                -- 002:3
  email        TEXT NOT NULL,                        -- 002:4
  display_name TEXT NULL,                            -- 005:1
  streak_days  INTEGER NOT NULL DEFAULT 0,           -- 005:2
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()    -- 002:5
);
collections (
  id                  UUID PK DEFAULT gen_random_uuid(),                 -- 001:2
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- 002:8-9
  name                TEXT NOT NULL,                                      -- 001:4
  source_language     TEXT NOT NULL DEFAULT 'pl',                         -- 003:2
  target_language     TEXT NOT NULL DEFAULT 'en',                         -- 003:3
  description         TEXT NOT NULL DEFAULT '',                           -- 004:2
  last_studied        TIMESTAMPTZ NULL,                                   -- 006:1
  progress            FLOAT NOT NULL DEFAULT 0,                           -- 006:2
  total_study_minutes INTEGER NOT NULL DEFAULT 0,                         -- 010:2
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()                  -- 001:5
);  -- idx_collections_user_id (001:17)
flashcards (
  id              UUID PK DEFAULT gen_random_uuid(),                          -- 001:9
  collection_id   UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE, -- 001:10
  source_text     TEXT NOT NULL,   -- renamed from polish_text  (007:1)
  target_text     TEXT NOT NULL,   -- renamed from english_text (007:2)
  position        INT NOT NULL DEFAULT 0,                                     -- 001:13
  srs_level       REAL NOT NULL DEFAULT 0.0,                                  -- 008:2
  last_studied_at TIMESTAMPTZ NULL DEFAULT NULL,                              -- 009:2
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()                         -- 001:14
);  -- idx_flashcards_collection_id (001:18)
```

Istotne dla seedowania/asercji:
- **`google_id` UNIQUE** — seed wielu userów musi mieć różne `google_id` (`002:3`).
- **Kaskady**: `users→collections` i `collections→flashcards` `ON DELETE CASCADE` — test usunięcia kolekcji powinien potwierdzić, że fiszki znikają z DB.
- **Brak CHECK** na językach, `srs_level`, `progress` — wszystkie ograniczenia to logika handlera (lub jej brak).
- `position` przy create fiszki: `COALESCE(MAX(position), -1) + 1` (pierwsza karta → 0) (`flashcards.rs:76-80`).

### C. Uwierzytelnianie i strategia JWT w testach

- `AuthUser::from_request` (`auth.rs:45-82`): wymaga `Authorization: Bearer <jwt>`, dekoduje HS256 z `JWT_SECRET`, `validate_aud = false`, `sub` musi być parsowalnym UUID. Braki → **401** (`Missing/Invalid Authorization header`, `Invalid or expired token`, `Invalid user ID in token`).
- `create_jwt` (`auth.rs:31-43`): `Claims { sub: user_id, exp: now + 30 dni }`, `Header::default()` (HS256).
- **W testach**: wstaw usera do DB (`INSERT INTO users (google_id, email, display_name) VALUES (...) RETURNING id`), potem wygeneruj JWT dla `id`. Dwa warianty:
  - jeśli powstanie `lib.rs` → reużyj `create_jwt(id, secret)`;
  - inaczej → zduplikuj minimalnie: `jsonwebtoken::encode(Header::default(), Claims{sub,exp}, EncodingKey::from_secret(secret))`.
- **Testy negatywne auth** (wspólne dla wszystkich chronionych tras): brak nagłówka → 401; `Bearer <śmieci>` → 401; token podpisany **innym** sekretem → 401; token z `sub` nie-UUID → 401; (opcjonalnie) token wygasły → 401.

### D. Ocena testowalności (tarcia)

- **Binarka bez `lib.rs`** (`Cargo.toml:3-5`, brak `[lib]`/`src/lib.rs`) → `tests/` nie widzi modułów wewnętrznych. (`main.rs:5-8` deklaruje `mod auth/handlers/models`, `pub mod error`.)
- **App inline** w `HttpServer::new(move || {...})` (`main.rs:105-143`) — brak `build_app()`. Bez refaktoru jedyna opcja to spawn pełnej binarki jako subprocess.
- **Env wymagane** (panic): `DATABASE_URL`, `JWT_SECRET`, `GOOGLE_CLIENT_ID` (`main.rs:55-58`); `PORT` opcjonalny (domyślnie 8080), bind `0.0.0.0:PORT` (`main.rs:50-53,144`). `DEPLOY_API_KEY` opcjonalny.
- **Migracje**: `sqlx::migrate!("./migrations").run(&pool)` (`main.rs:95-98`).
- **Brak istniejących testów** i brak `[dev-dependencies]` (`Cargo.toml`). Dostępne już: `reqwest 0.13`, `tokio 1.52` (full), `sqlx 0.9`, `uuid`, `serde_json`, `chrono`, `jsonwebtoken 10.4`. Do dodania (dev): `testcontainers` + `testcontainers-modules` (feature `postgres`).
- actix-web 4.13, tokio 1.52 → testy jako `#[tokio::test]`.

## Architecture Insights

**Rekomendowany kształt (do potwierdzenia w `/10x-plan`):**

1. **Cienki refaktor do `src/lib.rs`** (zalecane): przenieść `mod auth/handlers/models/error` pod `lib.rs`, dodać:
   - `pub fn build_app(pool, jwt, google, state) -> App<...>` z całym routingiem (wyciągnięty 1:1 z `main.rs:105-143`),
   - `pub async fn run_migrations(pool)`,
   - reeksport `create_jwt`.
   `main.rs` staje się cienkim wrapperem wołającym `lib`. Dzięki temu test startuje serwer **in-process** na `TcpListener` `127.0.0.1:0` (wolny port od OS), dostaje adres i uderza `reqwest` — to nadal „realny serwer + REST", ale bez kruchości subprocessu i z reużyciem `create_jwt`.
   - *Alternatywa bez refaktoru*: spawn binarki przez `std::process::Command` (`cargo run`/zbudowany exe), `PORT=0`? — actix nie wypisuje portu, więc trzeba by stałego portu per test (ryzyko kolizji) i parsowania gotowości. Gorsze; odnotowane jako plan B.

2. **Fixture testowy** (`tests/common/mod.rs`):
   - `start_postgres()` → `testcontainers` `postgres:16-alpine`, zwraca connection string + uchwyt kontenera (trzymany przy życiu na czas testu).
   - `setup()` → kontener + `PgPool` + `run_migrations` + `build_app`/spawn + JWT secret; zwraca `{ base_url, pool, secret }`.
   - `seed_user(pool, google_id, email)` → bezpośredni `INSERT ... RETURNING id`.
   - `jwt_for(user_id, secret)` → token.
   - Izolacja: **kontener per test** (najprostsze, pełna izolacja) lub współdzielony kontener + unikalni userzy per test (szybsze). Rekomendacja: zacząć od kontenera per plik/test-suite z czyszczeniem, zdecydować w planie.

3. **Wzorzec asercji**: akcja przez REST (`reqwest`) → sprawdź **status + ciało JSON** → następnie **zapytaj DB** osobnym poolem i porównaj stan (np. po `POST /collections` wiersz istnieje z poprawnym `user_id`, `source_language`; po `DELETE` brak wiersza; po `learning/complete` `total_study_minutes` wzrosło o `session_minutes`).

### Proponowana macierz testów (krytyczny dobór)

**Auth (cross-cutting)**: brak nagłówka→401; zły token→401; obcy sekret→401; `sub` nie-UUID→401.

**`GET /info`**: 200 + body zawiera nazwę crate'u.

**`POST /collections`**: (+) 201, wiersz w DB z `user_id`, językami, `description`; default `progress=0`, `total_study_minutes=0`; (−) blank/whitespace name→422; `source==target`→422; nieobsługiwany kod (`"xx"`)→422; brak pola (`target_language`)→400; brak JWT→401.

**`GET /collections`**: (+) zwraca tylko kolekcje danego usera (seed 2 userów, izolacja); pusty→`[]`; `flashcard_count` zgodny z liczbą fiszek w DB.

**`PUT /collections/{id}`**: (+) 200, zmiana widoczna w DB; (−) cudza/nieistniejąca→404; zły UUID w ścieżce→400; blank name→422; **bug-test**: odpowiedź ma `flashcard_count=0` mimo istniejących fiszek (`collections.rs:88-93`) — asercja dokumentująca/flagująca.

**`DELETE /collections/{id}`**: (+) 204, brak wiersza w DB **oraz** kaskadowe usunięcie fiszek; (−) cudza→404; powtórny DELETE→404.

**`GET /collections/{id}/flashcards`**: (+) 200 posortowane po `position`; (−) cudza→404; nieistniejąca→404.

**`POST /collections/{id}/flashcards`**: (+) 201, wiersz w DB, `position` auto (0,1,2…); (−) cudza→404; brak pola→400; **luka**: `source_text=""`/`target_text=""` → obecnie **201** (test dokumentuje i flaguje brak walidacji, `flashcards.rs:57-94`).

**`GET /collections/{id}/learning`**: (+) 200 posortowane po `position`; (−) **cudza/nieistniejąca → 200 `[]`** (NIE 404) — jawny test niespójności kontraktu vs #6 (`learning.rs:16-22`).

**`POST /collections/{id}/learning/complete`**: (+) 204, `progress` i `total_study_minutes += session_minutes` w DB, `last_studied` ustawione; (−) cudza→404; brak pola→400; **luka**: ujemne `session_minutes` zmniejsza `total_study_minutes` (obecnie przechodzi — test dokumentujący); `progress` poza [0,1] zapisywany dosłownie.

**`PUT /flashcards/{id}`**: (+) partial update — sam `source_text`, sam `srs_level`, `{}` jako no-op (200, brak zmian); zmiany widoczne w DB; (−) cudza→404; **quirk**: nie da się ustawić `last_studied_at=NULL` (COALESCE traktuje null jako „bez zmiany", `flashcards.rs:104-117`).

**`DELETE /flashcards/{id}`**: (+) 204, brak wiersza w DB; (−) cudza→404; **quirk**: brak renumeracji `position` pozostałych kart (gapy) — test dokumentujący, jeśli kolejność istotna.

**`POST /deploy`** (tylko odrzucenia): brak konfiguracji→503; zły/brak `X-Deploy-Key`→401; puste body→400. **Nigdy** happy-path (podmienia binarkę, ubija proces, `deploy.rs:60-68`).

## Code References

- `apps/backend/src/main.rs:50-58` — wymagane env (panic) + PORT.
- `apps/backend/src/main.rs:95-98` — `sqlx::migrate!`.
- `apps/backend/src/main.rs:105-143` — inline App (CORS permissive, Logger, Compress) + pełny routing.
- `apps/backend/src/auth.rs:31-43` — `create_jwt`.
- `apps/backend/src/auth.rs:45-82` — `AuthUser::from_request` (ścieżki 401).
- `apps/backend/src/handlers/collections.rs:9-13` — `validate_languages` + lista kodów.
- `apps/backend/src/handlers/collections.rs:88-93` — `PUT` zwraca `flashcard_count=0` (bug).
- `apps/backend/src/handlers/collections.rs:137-168` — `learning_complete` (brak bounds).
- `apps/backend/src/handlers/flashcards.rs:9-21` — `verify_collection_owner`.
- `apps/backend/src/handlers/flashcards.rs:57-94` — create fiszki (brak walidacji tekstu, auto-position).
- `apps/backend/src/handlers/flashcards.rs:104-117` — update przez COALESCE (quirk null).
- `apps/backend/src/handlers/learning.rs:16-22` — learning JOIN (cudza → `200 []`).
- `apps/backend/src/handlers/deploy.rs:7-69` — deploy (testować tylko 503/401/400).
- `apps/backend/migrations/001..010` — schemat (defaults, kaskady, rename 007).
- `apps/backend/Cargo.toml` — brak `[dev-dependencies]`; `reqwest/tokio/sqlx/jsonwebtoken` już obecne.

## Architecture Insights (podsumowanie decyzji)

- „Spawn realnego serwera + reqwest" najczyściej realizować **in-process** po cienkim refaktorze do `lib.rs` (port `0`, reużycie `create_jwt`), nie subprocessem.
- Walidacja językowa i nazw jest jedynie w handlerze collections; fiszki i `learning/complete` **nie mają walidacji** → to nie braki w testach, lecz cele testów negatywnych dokumentujących rzeczywiste (czasem błędne) zachowanie.
- Spójny kontrakt błędu to `{"error": "..."}` (JSON) — z wyjątkiem `/info` (text) i odrzuceń ekstraktorów actix (400 plain text). Asercje na ciało muszą to rozróżniać.

## Historical Context (from prior changes)

- `AGENTS.md:10-12` — twarde reguły: kody `pl/en/de/es/fr/it`, `source != target` → 422, pola `source_text/target_text` (od migracji 007). (Uwaga: `AGENTS.md:28` mówi „next migration 008" oraz schema bez `srs_level/last_studied_at/total_study_minutes` — **nieaktualne**; realny stan to migracje do 010.)
- `context/foundation/lessons.md:5-9` — backend jest jedynym klientem Supabase; testy łączą się z Postgresem bezpośrednio (w Dockerze), co jest zgodne.
- `context/archive/2026-06-18-srs-learning/plan.md` — migracja 008 (`srs_level`), kontrakt `PUT /flashcards/{id}` z `srs_level` przez COALESCE; ręczne checkpointy weryfikacyjne (kandydaci na testy).
- `context/archive/2026-06-18-srs-learning-2/plan.md` — migracja 009 (`last_studied_at`).
- `context/archive/2026-06-19-study-time-tracking/research.md` — migracja 010 (`total_study_minutes`), akumulacja `+= session_minutes`.
- `context/foundation/roadmap.md:63-64` — CI backendu uruchamia `cargo test` na push/PR do master → nowe testy integracyjne muszą działać w CI (wymaga Dockera w runnerze; do rozstrzygnięcia w planie).

## Related Research

- `context/archive/2026-06-18-srs-learning/research.md` — algorytm SRS i kontrakt API.
- `context/archive/2026-06-19-study-time-tracking/research.md` — śledzenie czasu nauki.

## Open Questions

1. **Refaktor `lib.rs` vs subprocess** — zalecany lib.rs (reużycie `create_jwt`, in-process, port 0). Akceptacja zmiany w kodzie produkcyjnym? (cienka, ale jednak zmiana struktury crate'u).
2. **Izolacja DB** — kontener per test (wolniej, pełna izolacja) vs współdzielony kontener + unikalni userzy/`TRUNCATE` między testami (szybciej). Do decyzji w planie.
3. **CI** — czy GitHub Actions runner ma Docker dla `testcontainers`? Jeśli nie, testy muszą być oznaczone/segregowane, by `cargo test` w CI nie padał.
4. **`SQLX_OFFLINE`/`.sqlx`** — projekt używa `sqlx::query_as` (runtime, nie makra `query!`), więc build testów nie wymaga DB w czasie kompilacji; potwierdzić, że nie ma makr `query!`/`query_as!` wymagających `.sqlx`.
5. **Czego NIE testować** — happy-path `/deploy` (destrukcyjny) i `/auth/login` (zewnętrzny Google) — potwierdzone jako poza zakresem.
6. **Bugi do zgłoszenia osobno** — `PUT /collections` `flashcard_count=0`, brak walidacji tekstu fiszki, brak bounds `learning/complete`: testy je udokumentują; czy traktować jako „expected" (assert obecnego zachowania) czy jako TODO-fix? Decyzja produktowa.
