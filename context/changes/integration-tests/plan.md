# Testy integracyjne backendu — Plan implementacji

## Przegląd

Dodajemy pakiet testów integracyjnych dla backendu Rust/Actix-web w `apps/backend/tests/`. Testy startują **realny serwer in-process** (na porcie przydzielonym przez OS), uderzają każdy endpoint przez **HTTP (`reqwest`)** i weryfikują wynik bezpośrednio w **PostgreSQL** uruchamianym w kontenerze Docker (`testcontainers`, `postgres:16-alpine`). Użytkownicy są seedowani wprost do DB; cała pozostała funkcjonalność testowana wyłącznie przez REST. JWT generowany w teście (HS256 + ten sam `JWT_SECRET`). Każdy etap testu komentowany po angielsku.

## Analiza stanu obecnego

- Backend to **binarka bez `lib.rs`**; konfiguracja `App` (routing + middleware + `app_data`) jest inline w `HttpServer::new(move || …)` (`apps/backend/src/main.rs:105-143`). Testy w `tests/` nie mają dostępu do `create_jwt` (`auth.rs:31-43`) ani budowy routingu.
- Wymagane env (panic przy braku): `DATABASE_URL`, `JWT_SECRET`, `GOOGLE_CLIENT_ID` (`main.rs:55-58`); `PORT` opcjonalny, bind `0.0.0.0:PORT` (`main.rs:50-53,144`); `DEPLOY_API_KEY` opcjonalny (`main.rs:60`).
- Migracje: `sqlx::migrate!("./migrations").run(&pool)` (`main.rs:95-98`), 10 migracji (001–010).
- `AuthUser` dekoduje JWT HS256 sekretem `JWT_SECRET`, `validate_aud=false`, `sub` musi być UUID (`auth.rs:45-82`); braki → 401.
- Brak istniejących testów i brak `[dev-dependencies]` (`Cargo.toml`). Dostępne: `reqwest 0.13`, `tokio 1.52` (full), `sqlx 0.9`, `jsonwebtoken 10.4`, `uuid`, `serde_json`, `chrono`.
- Walidacja istnieje tylko w collections (`collections.rs:9-13,40-47`); flashcards i `learning/complete` jej nie mają.
- Pełny schemat i katalog endpointów: `context/changes/integration-tests/research.md`.

## Pożądany stan końcowy

`cargo test` (z katalogu `apps/backend/`, przy działającym Dockerze) uruchamia kompletny pakiet testów integracyjnych: startuje jeden współdzielony kontener Postgres, stosuje migracje, i przechodzi wszystkie testy wszystkich endpointów (pozytywne + negatywne). `cargo test` bez tej flagi (a więc CI) nie uruchamia żadnego z tych testów. Binarka serwera działa identycznie jak przed refaktorem.

### Kluczowe odkrycia:

- `App` w actix ma trudny do zwrócenia typ — refaktor użyje wzorca „Zero-to-Production": `pub fn run(listener: TcpListener, deps) -> std::io::Result<Server>` zwraca konkretny `actix_web::dev::Server` (`main.rs:105-146` do przeniesienia).
- `sqlx::migrate!("./migrations")` rozwiązuje ścieżkę względem `CARGO_MANIFEST_DIR` w czasie kompilacji — działa tak samo z `lib.rs` (ten sam manifest).
- Endpointy są user-scoped, więc izolacja testów przez **unikalnego usera per test** wystarcza przy współdzielonym kontenerze.
- `dev-dependencies` są zawsze kompilowane przy `cargo test`, ale feature-gate na poziomie test-binarki sprawia, że w CI nie uruchamiają się żadne testy integracyjne.

## Czego NIE robimy

- **Nie naprawiamy** wykrytych bugów (`flashcard_count=0` w `PUT /collections`, brak walidacji tekstu fiszek, brak bounds w `learning/complete`, kontrakt `200 []` w `learning`). Testy **asertują obecne zachowanie** z komentarzem `// KNOWN ISSUE:` po angielsku. Naprawy to osobna zmiana.
- **Nie testujemy** `POST /auth/login` (zewnętrzny Google `tokeninfo`) ani happy-path `POST /deploy` (podmienia binarkę i ubija proces) — tylko ścieżki odrzucenia deploy.
- Nie uruchamiamy testów w CI (feature-gate domyślnie wyłączony).
- Nie zmieniamy zachowania żadnego endpointu ani schematu DB.
- Nie testujemy frontendu.

## Podejście do implementacji

Refaktor `lib.rs` (Faza 1) jest warunkiem wstępnym — udostępnia `run`, `run_migrations` i `create_jwt` testom oraz pozwala startować serwer in-process na porcie `0`. Faza 2 buduje harness (kontener + fixture + helpery) i przekrojowe testy auth. Fazy 3–5 pokrywają endpointy zasób po zasobie, każdy z kilkoma testami pozytywnymi i negatywnymi. Wzorzec każdego testu: **akcja przez REST → asercja status+JSON → asercja stanu w DB osobnym poolem**.

## Krytyczne szczegóły implementacji

- **Typ zwracany `App`**: nie zwracać `App` z funkcji. `run()` zwraca `Server`; routing wydzielony do `pub fn register_routes(cfg: &mut web::ServiceConfig)` wołanego przez `.configure(register_routes)` w obu ścieżkach (main + test). Middleware (`Cors::permissive()`, `Logger`, `Compress`) i `app_data` zostają w domknięciu `HttpServer::new`.
- **Port 0**: test tworzy `std::net::TcpListener::bind("127.0.0.1:0")`, odczytuje `local_addr().port()` PRZED przekazaniem do `run()` (actix nie wypisuje portu), buduje `base_url`, a serwer uruchamia przez `tokio::spawn(server)`.
- **Współdzielony kontener**: `static` przez `tokio::sync::OnceCell` (lub `std::sync::OnceLock` + async init) — kontener i pool tworzone raz na proces testowy; uchwyt kontenera trzymany żywy na czas całego biegu (drop = zatrzymanie kontenera).
- **Izolacja**: każdy test tworzy własnego usera z losowym `google_id` (np. `Uuid::new_v4()`), więc dane testów się nie kolidują mimo wspólnej bazy.

## Faza 1: Refaktor do `src/lib.rs`

### Przegląd
Wydzielić budowę aplikacji i pomocnicze funkcje do biblioteki, aby były dostępne dla testów; `main.rs` staje się cienkim wrapperem. Zero zmian zachowania.

### Wymagane zmiany:

#### 1. Nowa biblioteka crate'u
**Plik**: `apps/backend/src/lib.rs` (nowy)
**Cel**: Wystawić publiczne API dla binarki i testów. Przenieść deklaracje modułów (`auth`, `error`, `handlers`, `models`) z `main.rs` do `lib.rs` jako `pub mod`. Reeksportować `create_jwt`, `Claims`, `JwtConfig`, `GoogleConfig`, `AppState`.
**Kontrakt**:
- `pub mod auth; pub mod error; pub mod handlers; pub mod models;`
- `pub fn register_routes(cfg: &mut actix_web::web::ServiceConfig)` — rejestruje wszystkie trasy (`/info`, scope `/auth`, `/collections`, `/flashcards`, `/deploy`) dokładnie jak `main.rs:116-142`.
- `pub async fn run_migrations(pool: &sqlx::PgPool) -> Result<(), sqlx::migrate::MigrateError>` — opakowuje `sqlx::migrate!("./migrations").run(pool)`.
- `pub fn run(listener: std::net::TcpListener, pool: PgPool, jwt: JwtConfig, google: GoogleConfig, state: AppState) -> std::io::Result<actix_web::dev::Server>` — buduje `HttpServer::new(move || App::new().wrap(Cors::permissive()).wrap(Compress::default()).app_data(...).configure(register_routes))`, `.listen(listener)?`, `.run()`. Bez `Logger` (lub z — bez znaczenia; zachować zgodnie z main).
- Reeksport: `pub use auth::create_jwt;`

#### 2. Cienki `main.rs`
**Plik**: `apps/backend/src/main.rs`
**Cel**: Zostawić w `main` tylko: wczytanie env, inicjalizację loggera/panic hook, połączenie poola, `run_migrations`, budowę `TcpListener` z `PORT`, i wywołanie `fiszki_w_biegu_server::run(...).await`. Usunąć duplikację modułów i inline routingu.
**Kontrakt**: `main.rs` używa `use fiszki_w_biegu_server::{run, run_migrations, JwtConfig, GoogleConfig, AppState};`. Nazwa crate biblioteki = nazwa pakietu z myślnikami zamienionymi na podkreślenia (`fiszki_w_biegu_server`). `HttpServer` bind przez `TcpListener::bind(("0.0.0.0", port))?` przekazany do `run`.

#### 3. Deklaracja biblioteki w manifeście (jeśli wymagana)
**Plik**: `apps/backend/Cargo.toml`
**Cel**: Cargo automatycznie wykryje `src/lib.rs` i `src/main.rs` (lib + bin o nazwie pakietu). Zwykle bez zmian; dodać sekcję `[lib]`/`[[bin]]` tylko jeśli auto-wykrywanie wymaga doprecyzowania.
**Kontrakt**: lib name `fiszki_w_biegu_server`, bin pozostaje wykonywalny.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:
- Projekt się kompiluje: `cargo build`
- Brak ostrzeżeń o nieużywanym kodzie z refaktoru: `cargo build 2>&1` bez nowych warningów krytycznych
- Binarka linkuje lib: `cargo build --bin fiszki-w-biegu-server`

#### Weryfikacja ręczna:
- Uruchomiony serwer (`cargo run` z prawidłowym `.env`) odpowiada `200` na `GET /info` i `401` na `GET /collections` bez tokenu — zachowanie identyczne jak przed refaktorem.

**Uwaga implementacyjna**: Po zakończeniu fazy i przejściu weryfikacji automatycznych, zatrzymaj się na potwierdzenie ręczne, zanim przejdziesz dalej.

---

## Faza 2: Harness testowy (testcontainers, fixture, auth)

### Przegląd
Dodać zależności deweloperskie i wspólną infrastrukturę testów: współdzielony kontener Postgres, fixture `TestApp` startujący serwer, helpery seedowania usera i generowania JWT. Dodać smoke test i przekrojowe testy negatywne uwierzytelniania. Feature-gate izolujący testy od CI.

### Wymagane zmiany:

#### 1. Zależności deweloperskie i feature
**Plik**: `apps/backend/Cargo.toml`
**Cel**: Dodać `[dev-dependencies]` potrzebne testom i feature gate.
**Kontrakt**:
- `[dev-dependencies]`: `testcontainers` (najnowsza zgodna, np. 0.23), `testcontainers-modules` z feature `postgres`, `tokio` (już jest), `reqwest` (już jest, prod), `sqlx`, `serde_json`, `uuid`, `jsonwebtoken` — te już są w prod-deps i są dostępne w testach automatycznie; dodać tylko brakujące (`testcontainers`, `testcontainers-modules`).
- `[features]`: `integration-tests = []`.

#### 2. Moduł wspólny testów
**Plik**: `apps/backend/tests/common/mod.rs` (nowy)
**Cel**: Wspólna infrastruktura: start/współdzielenie kontenera, budowa `TestApp`, helpery. Cały plik i wywołania komentowane po angielsku.
**Kontrakt**:
- `async fn shared_db() -> (connection_string, &Container)` — `OnceCell` startujący `postgres:16-alpine` raz; uchwyt kontenera w `static`, by żył przez cały bieg.
- `struct TestApp { base_url: String, pool: PgPool, jwt_secret: String }`.
- `async fn spawn_app() -> TestApp` — pool do współdzielonego kontenera, `run_migrations(&pool)`, `TcpListener` na `127.0.0.1:0`, `local_addr` → `base_url`, ustal `JwtConfig`/`GoogleConfig{client_id:"test"}`/`AppState{deploy_api_key:None}` (lub `Some` w teście deploy), `tokio::spawn(run(...))`. Zwraca `TestApp`.
- `async fn seed_user(pool, google_id: &str, email: &str) -> Uuid` — `INSERT INTO users (google_id, email, display_name) VALUES ($1,$2,$3) RETURNING id`.
- `fn jwt_for(user_id: Uuid, secret: &str) -> String` — woła `fiszki_w_biegu_server::create_jwt`.
- `fn client() -> reqwest::Client` — klient HTTP (bez auto-redirect, by łatwo asertować statusy).

#### 3. Plik wejściowy testów (feature-gated)
**Plik**: `apps/backend/tests/integration.rs` (nowy)
**Cel**: Pojedyncza binarka testowa zawierająca moduły per zasób; cała gated feature'em, by CI ją pomijało.
**Kontrakt**:
- `#![cfg(feature = "integration-tests")]` na górze pliku.
- `mod common;` + `mod collections;` `mod flashcards;` `mod learning;` `mod auth;` `mod deploy;` (deklaracje modułów testowych dodawane w kolejnych fazach).
- Smoke test: `GET /info` → 200, body zawiera nazwę crate'u.

#### 4. Przekrojowe testy auth
**Plik**: `apps/backend/tests/auth.rs` (nowy; włączony jako `mod auth;`)
**Cel**: Sprawdzić odrzucenia `AuthUser` na reprezentatywnym chronionym endpoincie (`GET /collections`).
**Kontrakt** — testy (każdy z komentarzami po angielsku opisującymi krok):
- brak nagłówka `Authorization` → 401.
- `Authorization: Bearer not-a-jwt` → 401.
- token podpisany **innym** sekretem → 401.
- token z `sub` nie-UUID → 401 (zbudować ręcznie `Claims { sub: "abc" }`).
- (opcjonalnie) token wygasły (`exp` w przeszłości) → 401.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:
- Testy się kompilują: `cargo test --no-run`
- Smoke + auth przechodzą: `cargo test --test integration` (uruchamia smoke i `mod auth`)
- Bez feature CI nic nie uruchamia: `cargo test` kończy się bez testów integracyjnych
- Lint: `cargo clippy`

#### Weryfikacja ręczna:
- Z uruchomionym Dockerem pakiet startuje jeden kontener Postgres i kończy go po zakończeniu testów (brak osieroconych kontenerów: `docker ps -a`).

**Uwaga implementacyjna**: Zatrzymaj się na potwierdzenie ręczne po przejściu weryfikacji automatycznych.

---

## Faza 3: Testy collections

### Przegląd
Pokryć `GET/POST/PUT/DELETE /collections` testami pozytywnymi i negatywnymi, z asercją stanu w DB.

### Wymagane zmiany:

#### 1. Moduł testów collections
**Plik**: `apps/backend/tests/collections.rs` (nowy; `mod collections;`)
**Cel**: Komplet testów endpointów kolekcji. Wzorzec: REST → status+JSON → DB. Komentarze po angielsku.
**Kontrakt** — testy:
- **POST** (+): 201; wiersz w DB ma `user_id`, `name`, języki, `description`; defaulty `progress=0`, `total_study_minutes=0`.
- **POST** (−): blank/whitespace `name` → 422 `{"error":"Name must not be blank"}`; `source==target` → 422; kod spoza listy (`"xx"`) → 422; brak pola (`target_language`) → 400; brak JWT → 401.
- **GET list** (+): zwraca tylko kolekcje danego usera (seed 2 userów + ich kolekcje; user A nie widzi kolekcji B); pusty → `[]`; `flashcard_count` zgodny z liczbą wstawionych fiszek.
- **PUT** (+): 200; zmiana `name`/języków widoczna w DB. (−): cudza/nieistniejąca → 404; zły UUID w ścieżce → 400; blank name → 422.
- **PUT bug-doc**: `// KNOWN ISSUE:` odpowiedź zawiera `flashcard_count=0` mimo istniejących fiszek (`collections.rs:88-93`) — assert obecnego zachowania.
- **DELETE** (+): 204; brak wiersza w DB; kaskadowe usunięcie fiszek (wstaw fiszki, usuń kolekcję, sprawdź `SELECT COUNT(*) FROM flashcards` = 0). (−): cudza → 404; powtórny DELETE → 404.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:
- Testy collections przechodzą: `cargo test collections`
- Lint: `cargo clippy`

#### Weryfikacja ręczna:
- Przegląd komentarzy: każdy test ma czytelny opis kroków po angielsku; bug-doc wyraźnie oznaczony `KNOWN ISSUE`.

**Uwaga implementacyjna**: Zatrzymaj się na potwierdzenie ręczne.

---

## Faza 4: Testy flashcards

### Przegląd
Pokryć `GET/POST /collections/{id}/flashcards`, `PUT/DELETE /flashcards/{id}` testami pozytywnymi i negatywnymi.

### Wymagane zmiany:

#### 1. Moduł testów flashcards
**Plik**: `apps/backend/tests/flashcards.rs` (nowy; `mod flashcards;`)
**Cel**: Komplet testów fiszek. Komentarze po angielsku.
**Kontrakt** — testy:
- **GET list** (+): 200, posortowane po `position`; (−): cudza kolekcja → 404; nieistniejąca → 404; brak JWT → 401.
- **POST** (+): 201; wiersz w DB; `position` auto-inkrementowane (0,1,2…) przy kolejnych create. (−): cudza kolekcja → 404; brak pola → 400.
- **POST bug-doc**: `// KNOWN ISSUE:` `source_text=""`/`target_text=""` → 201 (brak walidacji, `flashcards.rs:57-94`) — assert obecnego zachowania.
- **PUT** (+): partial update — sam `source_text`; sam `srs_level`; `{}` jako no-op (200, wiersz bez zmian); zmiany widoczne w DB. (−): cudza → 404.
- **PUT quirk-doc**: `// KNOWN ISSUE:` nie da się ustawić `last_studied_at=NULL` (COALESCE traktuje null jako brak zmiany, `flashcards.rs:104-117`).
- **DELETE** (+): 204; brak wiersza w DB. (−): cudza → 404.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:
- Testy flashcards przechodzą: `cargo test flashcards`
- Lint: `cargo clippy`

#### Weryfikacja ręczna:
- Przegląd: bug-doc/quirk-doc wyraźnie oznaczone i poprawnie asertują obecne zachowanie.

**Uwaga implementacyjna**: Zatrzymaj się na potwierdzenie ręczne.

---

## Faza 5: Learning, deploy i finalizacja

### Przegląd
Pokryć `GET /collections/{id}/learning`, `POST /collections/{id}/learning/complete` oraz ścieżki odrzucenia `POST /deploy`. Dodać feature-gate finalnie i instrukcję uruchomienia lokalnego.

### Wymagane zmiany:

#### 1. Moduł testów learning
**Plik**: `apps/backend/tests/learning.rs` (nowy; `mod learning;`)
**Cel**: Testy sesji nauki i zapisu jej wyniku. Komentarze po angielsku.
**Kontrakt** — testy:
- **GET learning** (+): 200, fiszki posortowane po `position`.
- **GET learning bug-doc**: `// KNOWN ISSUE:` cudza/nieistniejąca kolekcja → **200 `[]`** (nie 404; niespójne z `GET .../flashcards`, `learning.rs:16-22`) — assert obecnego zachowania.
- **learning/complete** (+): 204; w DB `progress` zaktualizowane, `total_study_minutes += session_minutes`, `last_studied` ustawione (NOT NULL po wywołaniu). Drugie wywołanie akumuluje minuty.
- **learning/complete** (−): cudza → 404; brak pola → 400.
- **learning/complete bug-doc**: `// KNOWN ISSUE:` ujemne `session_minutes` zmniejsza `total_study_minutes` (brak bounds, `collections.rs:137-168`) — assert obecnego zachowania.

#### 2. Moduł testów deploy
**Plik**: `apps/backend/tests/deploy.rs` (nowy; `mod deploy;`)
**Cel**: Tylko ścieżki odrzucenia (nigdy happy-path — podmienia binarkę). Komentarze po angielsku.
**Kontrakt** — testy (wymaga wariantu `spawn_app` z konfigurowalnym `deploy_api_key`):
- `AppState.deploy_api_key = None` → `POST /deploy` → 503 `{"error":"Deploy endpoint is not configured"}`.
- `deploy_api_key = Some("k")`, brak/zły `X-Deploy-Key` → 401 `{"error":"Invalid deploy key"}`.
- `deploy_api_key = Some("k")`, poprawny klucz, **puste body** → 400 `{"error":"Empty payload"}`. (Nie wysyłać niepustego body — uniknąć podmiany binarki.)

#### 3. Dokumentacja uruchomienia
**Plik**: `apps/backend/tests/README.md` (nowy) lub sekcja w `apps/backend` README
**Cel**: Opisać po polsku/angielsku: wymóg Dockera, komendę `cargo test`, fakt że CI ich nie uruchamia.
**Kontrakt**: krótka instrukcja + przykładowa komenda.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:
- Cały pakiet przechodzi: `cargo test`
- CI-mode pomija integracje: `cargo test` (bez feature) nie uruchamia testów integracyjnych
- Lint całości: `cargo clippy`

#### Weryfikacja ręczna:
- `docker ps -a` po biegu: brak osieroconych kontenerów.
- Przegląd: każdy endpoint ma ≥2 testy (pozytywny + negatywny), bugi udokumentowane, komentarze po angielsku, instrukcja uruchomienia kompletna.

**Uwaga implementacyjna**: Zatrzymaj się na końcowe potwierdzenie ręczne.

---

## Strategia testowania

### Testy integracyjne (sedno tej zmiany):
- Wzorzec: akcja przez REST (`reqwest`) → asercja `status` + ciało JSON → asercja stanu w DB osobnym poolem `sqlx`.
- Userzy seedowani wprost do DB; reszta wyłącznie via REST.
- Izolacja: unikalny `google_id`/user per test na współdzielonym kontenerze.
- Pokrycie: każdy endpoint ≥2 testy (pozytywny + ≥1 negatywny/błędne dane).

### Kroki testowania ręcznego:
1. `cd apps/backend && cargo test` przy uruchomionym Dockerze → wszystko zielone.
2. `cargo test` (bez feature) → testy integracyjne pominięte.
3. `docker ps -a` → brak osieroconych kontenerów.
4. `cargo run` z `.env` → `GET /info` 200, `GET /collections` bez tokenu 401 (regresja po refaktorze).

## Uwagi dotyczące wydajności

Jeden współdzielony kontener na cały bieg minimalizuje narzut (start kontenera ~kilka sekund raz). Testy w jednej binarce domyślnie biegną wielowątkowo — izolacja per-user to umożliwia bez kolizji.

## Uwagi dotyczące migracji

Brak migracji DB. Jedyna zmiana produkcyjna to refaktor strukturalny crate'u (lib+bin) bez zmiany zachowania runtime.

## Referencje

- Powiązane badania: `context/changes/integration-tests/research.md`
- Konfiguracja serwera (do wydzielenia): `apps/backend/src/main.rs:105-146`
- JWT: `apps/backend/src/auth.rs:31-43`, ekstraktor `auth.rs:45-82`
- Walidacja collections: `apps/backend/src/handlers/collections.rs:9-13,40-47`
- Bugi/quirki: `collections.rs:88-93`, `flashcards.rs:57-94,104-117`, `learning.rs:16-22`, `collections.rs:137-168`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku. Nie zmieniaj nazw tytułów kroków. Zobacz `references/progress-format.md`.

### Faza 1: Refaktor do src/lib.rs

#### Automatyczne
- [x] 1.1 Projekt się kompiluje: `cargo build` — 6dd23a4
- [x] 1.2 Brak nowych krytycznych warningów: `cargo build 2>&1` — 6dd23a4
- [x] 1.3 Binarka linkuje lib: `cargo build --bin fiszki-w-biegu-server` — 6dd23a4

#### Ręczne
- [x] 1.4 Serwer odpowiada 200 na GET /info i 401 na GET /collections bez tokenu (zachowanie jak przed refaktorem) — 6dd23a4

### Faza 2: Harness testowy (testcontainers, fixture, auth)

#### Automatyczne
- [x] 2.1 Testy się kompilują: `cargo test --no-run` — 318eba8
- [x] 2.2 Smoke + auth przechodzą: `cargo test --test integration` — 318eba8
- [x] 2.3 Bez feature CI nic nie uruchamia: `cargo test` — 318eba8
- [x] 2.4 Lint: `cargo clippy` — 318eba8

#### Ręczne
- [x] 2.5 Jeden kontener startuje i kończy się; brak osieroconych (`docker ps -a`) — 318eba8

### Faza 3: Testy collections

#### Automatyczne
- [x] 3.1 Testy collections przechodzą: `cargo test collections` — ea5423d
- [x] 3.2 Lint: `cargo clippy` — ea5423d

#### Ręczne
- [x] 3.3 Przegląd komentarzy po angielsku; bug-doc oznaczony KNOWN ISSUE — ea5423d

### Faza 4: Testy flashcards

#### Automatyczne
- [x] 4.1 Testy flashcards przechodzą: `cargo test flashcards` — 5e3ee53
- [x] 4.2 Lint: `cargo clippy` — 5e3ee53

#### Ręczne
- [x] 4.3 Przegląd: bug-doc/quirk-doc poprawnie asertują obecne zachowanie — 5e3ee53

### Faza 5: Learning, deploy i finalizacja

#### Automatyczne
- [x] 5.1 Cały pakiet przechodzi: `cargo test` — 7d51555
- [x] 5.2 CI-mode pomija integracje: `cargo build` (nie kompiluje testów/testcontainers) — 7d51555
- [x] 5.3 Lint całości: `cargo clippy` — 7d51555

#### Ręczne
- [x] 5.4 Brak osieroconych kontenerów (`docker ps -a`); każdy endpoint ≥2 testy; instrukcja uruchomienia kompletna — 7d51555
