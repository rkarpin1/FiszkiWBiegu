# Repository Guidelines

FiszkiWBiegu is an audio flashcard app for runners (Polish↔English vocabulary, hands-free). Monorepo with two independent build systems: Rust/Actix-web backend (`backend/`) and Kotlin Multiplatform frontend (`frontend/`) targeting Android and Web (WASM).

## Hard Rules

- Audio playback during workouts must use Android Foreground Service with a system notification — Android kills background processes (`@context/foundation/prd.md:106`).
- All delete operations must show a confirmation dialog; there is no undo (`@context/foundation/prd.md:99`).
- MVP scope is Android only. Do not add web learning mode, iOS features, spaced repetition, file import, or payments (`@context/foundation/prd.md:140`).
- Shared cross-platform logic goes in `frontend/shared/src/commonMain/`; platform-specific code in `androidMain/` or `wasmJsMain/`.

## Project Structure

- `backend/` — Rust/Actix-web API; see `@backend/Cargo.toml`
- `frontend/shared/` — KMP library (commonMain, androidMain, iosMain, wasmJsMain)
- `frontend/androidApp/` — Android target
- `frontend/webApp/` — Web/WASM target
- `frontend/iosApp/` — iOS Xcode project (out of MVP scope)
- `context/` — PRD, tech-stack decisions, planning docs

## Build, Test, and Dev Commands

Run `./gradlew` commands from `frontend/`; run `cargo` commands from `backend/`.

- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Web dev server (WASM): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Shared KMP tests: `./gradlew :shared:test`
- Backend build: `cargo build`
- Backend run: `cargo run`
- Backend tests: `cargo test`

## Key Versions

Kotlin 2.3.21 · Compose Multiplatform 1.11.0 · Android compileSdk 37 / minSdk 30 · Ktor 3.5.0 · Koin 4.2.1 — see `@frontend/gradle/libs.versions.toml`. Rust Actix-web 4.13.0 · Tokio 1.52.3 — see `@backend/Cargo.toml`.

## Code Conventions

No detekt, lint, or format tooling is configured — follow the style of sibling files. Tests use `kotlin.test` and live in `frontend/shared/src/commonTest/`. Rust uses `#[test]`. No CI is configured.

## Commits

Pattern: `{module}: {description in Polish}` — e.g., `backend: Drobne zmiany`, `frontend: Zmiany w konfiguracji projektu`. PR target: https://github.com/rkarpin1/FiszkiWBiegu.
