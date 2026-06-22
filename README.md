# FiszkiWBiegu

Audio flashcard app for runners — Polish↔English vocabulary, hands-free. Kotlin Multiplatform monorepo targeting Android and Web (WebAssembly), with a Rust/Actix-web backend.

## Project Structure

```
apps/
  backend/        — Rust/Actix-web API (Supabase/PostgreSQL)
  frontend/
    androidApp/   — Android target
    webApp/       — Web target (WASM + JS)
    shared/       — Shared KMP business logic
```

## Running the Web App (WebAssembly)

All Gradle commands are run from `apps/frontend/`.

### Development server

```bash
cd apps/frontend
./gradlew :webApp:composeCompatibilityBrowserDistribution
```
Deploy a dir `webApp/build/dist/composeWebCompatibility/productionExecutable` to a web server

Starts a local dev server with hot reload. Open the URL printed in the console (typically `http://localhost:8080`).

### Production build

```bash
cd apps/frontend
./gradlew :webApp:wasmJsBrowserProductionWebpack
```

#### Web — Wasm target (faster, modern browsers)
```bash
cd apps/frontend
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```


Output lands in `apps/frontend/webApp/build/dist/wasmJs/productionExecutable/`.

### JS target (fallback)

The `webApp` module also compiles to plain JS. To run the JS dev server instead:

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

## Running the Android App

```bash
cd apps/frontend
./gradlew :androidApp:assembleDebug
```

Install the APK from `androidApp/build/outputs/apk/debug/` onto a device or emulator (Android 11+, API 30).

## Running the Backend

```bash
cd apps/backend
cargo run
```

Requires a `.env` file with `DATABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`. See `apps/backend/` for details.

## Running Tests

```bash
cd apps/frontend
./gradlew :shared:test
```
