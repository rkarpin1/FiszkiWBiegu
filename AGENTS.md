# Repository Guidelines

FiszkiWBiegu is an audio flashcard app for runners (Polish↔English vocabulary, hands-free). Monorepo with two independent build systems: Rust/Actix-web backend (`apps/backend/`) and Kotlin Multiplatform frontend (`apps/frontend/`) targeting Android and Web (WASM).

## Hard Rules

- Audio playback during workouts must use Android Foreground Service with a system notification — Android kills background processes (`@context/foundation/prd.md:106`).
- All delete operations must show a confirmation dialog; there is no undo (`@context/foundation/prd.md:99`).
- MVP scope is Android only. Do not add web learning mode, iOS features, file import, or payments (`@context/foundation/prd.md:140`). SRS is shipped (`flashcards.srs_level`); the audio learning session is Android-only.
- Backend database is Supabase (PostgreSQL) — connect via `sqlx` using `DATABASE_URL` (session-mode pooler, port 5432). Always use `SUPABASE_SERVICE_ROLE_KEY` server-side; never the anon key.
- Flashcard text fields are `source_text` / `target_text` (renamed from `polish_text`/`english_text` in migration 007). Never use the old names in code or SQL.
- Collection language fields are `source_language` / `target_language`. Accepted codes: `pl`, `en`, `de`, `es`, `fr`, `it`; source ≠ target enforced by backend (422).

## Project Structure

Full layout and run instructions: `@README.md`.

- `apps/backend/` — Rust/Actix-web API; see `@apps/backend/Cargo.toml`
- `apps/frontend/shared/` — shared KMP business logic (common data/domain layer)
- `apps/frontend/androidApp/` — Android target; `LearningService.kt` (Foreground Service + TTS + MediaSession)
- `apps/frontend/webApp/` — Web target (WASM + JS fallback); CRUD only, no learning mode
- `apps/frontend/iosApp/` — iOS Xcode project (out of MVP scope)

## DB Schema

Tables: `users`, `collections`, `flashcards`. Full schema (columns, types, FKs): `@context/docs/database.md`.

`flashcard_count` is not a column — the backend computes it per query as `COUNT(*)`.

Migrations live in `apps/backend/migrations/`. Next migration number: **011**.

## API Endpoints

All endpoints require `Authorization: Bearer <jwt>` except `GET /info`, `POST /auth/login`, and `POST /deploy` (the last uses the `X-Deploy-Key` header). Full endpoint list + spec: `@context/docs/openapi.yaml`.

## Build, Test, and Dev Commands

Run `./gradlew` commands from `apps/frontend/`; run `cargo` commands from `apps/backend/`.

- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Web dev server (WASM): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Shared KMP tests: `./gradlew :shared:test`
- Backend run: `cargo run` (needs `.env` with `DATABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`)
- Backend tests: `cargo test` — integration suite spins up Postgres via testcontainers, so Docker must be running

## Key Versions

See @apps/frontend/gradle/libs.versions.toml and @apps/backend/Cargo.toml for pinned versions.

## Code Conventions

No detekt, lint, or format tooling is configured. KMP tests use `kotlin.test` and live in `apps/frontend/shared/src/commonTest/`. Backend integration tests live in `apps/backend/tests/` (`integration.rs` + per-area modules) and hit a real Postgres via testcontainers.

## Commits

Descriptions in Polish. Feature work uses Conventional Commits with a change-id scope — e.g. `feat(flashcard-translate): ...`, `chore(integration-tests): ...`; incidental backend changes use a module prefix — e.g. `backend: Bump wersji 0.1.8`. PR target: https://github.com/rkarpin1/FiszkiWBiegu.
