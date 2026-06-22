# Testy integracyjne backendu — Krótki plan

> Pełny plan: `context/changes/integration-tests/plan.md`
> Badania: `context/changes/integration-tests/research.md`

## Co i dlaczego

Backend Rust/Actix-web nie ma żadnych testów. Dodajemy pakiet testów integracyjnych w `apps/backend/tests/`, który uderza każdy endpoint przez prawdziwe HTTP i weryfikuje wynik w bazie — siatka bezpieczeństwa przeciw regresjom API i kontraktu DB.

## Punkt wyjścia

Crate to binarka bez `lib.rs`, z konfiguracją `App` inline w `main.rs:105-143`; brak testów i `[dev-dependencies]`. Dostępne już `reqwest`, `tokio`, `sqlx`, `jsonwebtoken`. JWT HS256 z `JWT_SECRET` (`auth.rs`), migracje przez `sqlx::migrate!` (`main.rs:95-98`). Image `postgres:16-alpine` jest zainstalowany.

## Pożądany stan końcowy

`cargo test --features integration-tests` (przy działającym Dockerze) startuje jeden kontener Postgres, stosuje migracje i przechodzi wszystkie testy wszystkich endpointów (pozytywne + negatywne). `cargo test` bez flagi (CI) ich nie uruchamia. Serwer działa identycznie jak przed refaktorem.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego | Źródło |
|---|---|---|---|
| Warstwa startu | Spawn realnego serwera in-process + `reqwest` | „via REST, wynik w DB"; bez kruchego subprocessu | Badania |
| Docker | `testcontainers` (`postgres:16-alpine`) | Auto start/stop, izolacja, wszystko w Rust | Badania |
| `/auth/login` | Pominięty | Zewnętrzny Google `tokeninfo` — nietestowalny lokalnie | Badania |
| Udostępnienie serwera/JWT | Cienki `src/lib.rs` (`run`/`register_routes`/`run_migrations` + reeksport `create_jwt`) | Idiomatyczne, reużycie `create_jwt`, in-process port 0 | Plan |
| Cykl życia DB | 1 współdzielony kontener (OnceCell) + unikalny user/JWT per test | Szybko + naturalna izolacja (endpointy user-scoped) | Plan |
| Wykryte bugi | Assert obecnego zachowania + komentarz `KNOWN ISSUE` (EN) | Suite zielony, dokumentuje rzeczywistość, zero scope creep | Plan |
| Zakres napraw | Tylko testy (jedyna zmiana prod = wydzielenie lib.rs) | Skupiona, zgodna z zadaniem zmiana | Plan |
| CI | Poza CI — feature-gate `integration-tests` | Szybkie CI; testy egzekwowane lokalnie | Plan |

## Zakres

**W zakresie:** refaktor do `lib.rs`; harness (testcontainers + fixture + helpery); testy wszystkich endpointów aplikacyjnych (collections, flashcards, learning); ścieżki odrzucenia `deploy`; przekrojowe testy auth (401); feature-gate + instrukcja.

**Poza zakresem:** naprawa bugów (tylko dokumentowane testami); `POST /auth/login`; happy-path `deploy`; uruchamianie w CI; zmiany schematu/zachowania; frontend.

## Architektura / Podejście

`lib.rs` wystawia `run(listener, deps) -> Server` (konkretny typ, omija problem opaque-type `App`) i `register_routes(cfg)`; `main.rs` to cienki wrapper. Test binduje `TcpListener` na `127.0.0.1:0`, odczytuje port, spawnuje serwer, uderza `reqwest`. Współdzielony kontener Postgres przez `OnceCell`; każdy test seeduje własnego usera i generuje JWT. Wzorzec asercji: REST → status+JSON → stan w DB (osobny pool `sqlx`).

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|---|---|---|
| 1. Refaktor lib.rs | `run`/`register_routes`/`run_migrations` + cienki main; bez zmiany zachowania | Typ zwracany `App` w actix — rozwiązany przez `Server`+`configure` |
| 2. Harness | dev-deps, kontener (OnceCell), `TestApp`, helpery, smoke + auth 401, feature-gate | Cykl życia kontenera / osierocone kontenery |
| 3. Collections | testy list/create/update/delete (+422/404/400/401, bug-doc `flashcard_count`) | Pokrycie ownership/walidacji |
| 4. Flashcards | testy list/create/update/delete (+404, puste teksty 201, partial, kaskada) | Udokumentowanie braku walidacji |
| 5. Learning + deploy + finalizacja | learning (200 [] bug), complete (akumulacja, ujemne minuty), deploy 503/401/400, instrukcja | Nie dotknąć happy-path deploy |

**Wymagania wstępne:** Docker uruchomiony lokalnie; image `postgres:16-alpine` (jest).
**Szacowany nakład pracy:** ~4–5 sesji w 5 fazach.

## Otwarte ryzyka i założenia

- Refaktor `lib.rs` zmienia strukturę crate'u — musi być bezbłędnie neutralny dla zachowania (Faza 1 to weryfikuje).
- `testcontainers` wymaga Dockera; bez niego testy nie ruszą (akceptowane — to świadomie lokalne).
- Testy domyślnie biegną równolegle; izolacja opiera się na unikalnych userach per test.
- Bugi zakodowane w testach trzeba zaktualizować, gdy powstanie zmiana naprawcza.

## Kryteria sukcesu (podsumowanie)

- Każdy endpoint aplikacyjny ma ≥2 testy (pozytywny + negatywny/błędne dane), wszystkie zielone przez `cargo test --features integration-tests`.
- Wynik każdej akcji potwierdzony w DB, nie tylko w odpowiedzi HTTP.
- `cargo test` (CI) nie uruchamia testów integracyjnych; brak osieroconych kontenerów po biegu.
