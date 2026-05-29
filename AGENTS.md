# Repository Guidelines

FiszkiWBiegu is an audio flashcard app for runners (Polish↔English vocabulary, hands-free). Monorepo with two independent build systems: Rust/Actix-web backend (`apps/backend/`) and Kotlin Multiplatform frontend (`apps/frontend/`) targeting Android and Web (WASM).

## Hard Rules

- Audio playback during workouts must use Android Foreground Service with a system notification — Android kills background processes (`@context/foundation/prd.md:106`).
- All delete operations must show a confirmation dialog; there is no undo (`@context/foundation/prd.md:99`).
- MVP scope is Android only. Do not add web learning mode, iOS features, spaced repetition, file import, or payments (`@context/foundation/prd.md:140`).
- Shared cross-platform logic goes in `apps/frontend/shared/src/commonMain/`; platform-specific code in `androidMain/` or `wasmJsMain/`.
- Backend database is Supabase (PostgreSQL) — connect via `sqlx` using `DATABASE_URL` (session-mode pooler, port 5432). Always use `SUPABASE_SERVICE_ROLE_KEY` server-side; never the anon key.

## Project Structure

- `apps/backend/` — Rust/Actix-web API; see `@apps/backend/Cargo.toml`
- `apps/frontend/shared/` — KMP library (commonMain, androidMain, iosMain, wasmJsMain)
- `apps/frontend/androidApp/` — Android target
- `apps/frontend/webApp/` — Web/WASM target
- `apps/frontend/iosApp/` — iOS Xcode project (out of MVP scope)
- `context/` — PRD, tech-stack decisions, planning docs

## Build, Test, and Dev Commands

Run `./gradlew` commands from `apps/frontend/`; run `cargo` commands from `apps/backend/`.

- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Web dev server (WASM): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Shared KMP tests: `./gradlew :shared:test`
- Backend build: `cargo build`
- Backend run: `cargo run`
- Backend tests: `cargo test`

## Key Versions

See @apps/frontend/gradle/libs.versions.toml and @apps/backend/Cargo.toml for pinned versions.

## Code Conventions

No detekt, lint, or format tooling is configured. Kotlin: PascalCase for classes/objects, camelCase for functions and properties, file named after its primary class. Tests use `kotlin.test` and live in `apps/frontend/shared/src/commonTest/`.

## Commits

Pattern: `{module}: {description in Polish}` — e.g., `backend: Drobne zmiany`, `frontend: Zmiany w konfiguracji projektu`. PR target: https://github.com/rkarpin1/FiszkiWBiegu.
