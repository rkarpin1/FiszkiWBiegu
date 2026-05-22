---
bootstrapped_at: 2026-05-22T13:30:19Z
starter_id: rust
starter_name: "Rust (binary crate)"
project_name: fiszki-w-biegu
language_family: multi
package_manager: cargo
cwd_strategy: subdir-then-move
bootstrapper_confidence: best-effort
phase_3_status: ok
audit_command: "null"
---

## Hand-off

```yaml
starter_id: rust
package_manager: cargo
project_name: fiszki-w-biegu
hints:
  language_family: multi
  team_size: solo
  deployment_target: fly
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: best-effort
  path_taken: custom
  quality_override: true
  self_check_answers:
    typed: true
    from_official_starter: false
    conventions: true
    docs_current: true
    can_judge_agent: true
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: true
```

### Why this stack

Solo developer building FiszkiWBiegu — a hands-free flashcard audio app for runners.
The project is two components: a Rust/Actix-web backend API (cloud storage for flashcards,
OAuth Google auth) in `backend/`, and a Kotlin Multiplatform frontend (Kotlin 2.3.21 +
Compose Multiplatform 1.11.0) in `frontend/`. The KMP frontend targets Android (foreground
audio service, MediaSession headphone control for learning mode) and Web (flashcard
management UI) from a single shared Kotlin codebase; iOS is not configured in MVP.
No single registry starter covers this combination; `rust` binary crate is the bootstrapper
entry point for the backend, with actix-web added manually after scaffolding. The KMP
frontend is set up separately in Android Studio. quality_override is set because Actix-web
is not in the registry and the KMP frontend has no bootstrapper support.

## Pre-scaffold verification

| Signal      | Value                                                          | Severity | Notes                                                              |
| ----------- | -------------------------------------------------------------- | -------- | ------------------------------------------------------------------ |
| npm package | not run                                                        | n/a      | non-JS starter — cmd_template calls cargo, not an npm CLI          |
| GitHub repo | not run                                                        | n/a      | docs_url (https://doc.rust-lang.org/book/) is not a GitHub repo URL |

Local toolchain: cargo 1.95.0 (2026-03-21) — fresh.

## Scaffold log

**Resolved invocation**: `cargo new .bootstrap-scaffold --bin --edition 2024 --name fiszki-w-biegu`
**Note**: `--name fiszki-w-biegu` added because cargo rejects package names starting with `.`; directory name `.bootstrap-scaffold` is the temp dir, package name is set explicitly.
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 2 (Cargo.toml, src/main.rs)
**Conflicts (.scaffold siblings)**: none
**.gitignore handling**: absent in scaffold (cargo skipped generation — cwd already contains `.git`)
**.bootstrap-scaffold cleanup**: deleted

## Post-scaffold audit

**Tool**: skipped — no built-in audit tool for multi
**Recommended external tool**: For the Rust backend component, run `cargo audit` after adding dependencies. For the Kotlin/Android component (set up separately in Android Studio), use Android Studio's built-in dependency vulnerability checker or `./gradlew dependencyCheckAnalyze` with OWASP Dependency-Check plugin.

## Hints recorded but not acted on

| Hint                    | Value                                                                                                  |
| ----------------------- | ------------------------------------------------------------------------------------------------------ |
| bootstrapper_confidence | best-effort                                                                                            |
| quality_override        | true                                                                                                   |
| path_taken              | custom                                                                                                 |
| self_check_answers      | typed: true, from_official_starter: false, conventions: true, docs_current: true, can_judge_agent: true |
| team_size               | solo                                                                                                   |
| deployment_target       | fly                                                                                                    |
| ci_provider             | github-actions                                                                                         |
| ci_default_flow         | auto-deploy-on-merge                                                                                   |
| has_auth                | true                                                                                                   |
| has_payments            | false                                                                                                  |
| has_realtime            | false                                                                                                  |
| has_ai                  | false                                                                                                  |
| has_background_jobs     | true                                                                                                   |

Note: `quality_override: true` and `bootstrapper_confidence: best-effort` appear here — v1 surfaces but does not compensate. A future M1L4 skill will generate CLAUDE.md / AGENTS.md with ecosystem-specific guidance for Actix-web and Kotlin Multiplatform.

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

### Stan projektu (2026-05-22)

**Backend** (`backend/`):
- `cargo new` — wykonano; `Cargo.toml` z `name = "fiszki-w-biegu"`, `edition = "2024"`
- `actix-web 4.13.0` + `tokio 1.52.3` — dodano (`cargo add`)
- `src/main.rs` — bazowy serwer HTTP na porcie 8080 z endpointem `GET /health`
- `cargo build` — OK

**Frontend** (`frontend/`):
- Kotlin Multiplatform (Kotlin 2.3.21 + Compose Multiplatform 1.11.0) — skonfigurowany w Android Studio
- Moduły: `androidApp`, `shared`, `webApp` (JS + WasmJS)
- Dodane biblioteki w `shared`: Ktor 3.1.3, kotlinx-serialization 1.8.1, kotlinx-datetime 0.6.2, kotlinx-coroutines 1.10.2, Koin 4.0.3, Compose Navigation 2.8.0-alpha10, Multiplatform Settings 1.2.0

### Pozostałe kroki ręczne

- `.git` już zainicjowany — wykonaj pierwszy commit po zatwierdzeniu bieżącego stanu.
- Dodaj `androidx.credentials` + `com.google.android.libraries.identity.googleid` do `androidApp` (OAuth Google — FR-001).
- Dodaj SQLDelight jeśli zdecydujesz się na lokalną bazę danych do trybu offline.
- Uruchom `cargo audit` po dodaniu kolejnych zależności w backendzie.
- Skonfiguruj CI/CD (`github-actions`, `auto-deploy-on-merge`) i deployment (`fly`) — na razie ręcznie.
