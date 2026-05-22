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
The project is genuinely two applications: a Rust/Actix-web backend API (cloud storage
for flashcards, OAuth Google auth) and a native Android app (Kotlin + Jetpack Compose,
foreground audio service, MediaSession headphone control). No single registry starter
covers this combination; `rust` binary crate is the bootstrapper entry point for the
backend, with actix-web added manually after scaffolding. The Android app is set up
separately in Android Studio — bootstrapper does not scaffold it. Flutter was the
alternative that would have covered Android + Web from one Dart codebase, but the user
chose native Kotlin for full Android API access. quality_override is set because
Actix-web is not in the registry and the Android component has no bootstrapper support.

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

Note: `quality_override: true` and `bootstrapper_confidence: best-effort` appear here — v1 surfaces but does not compensate. A future M1L4 skill will generate CLAUDE.md / AGENTS.md with ecosystem-specific guidance for Actix-web and Kotlin/Jetpack Compose.

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- Your `.git` is already initialised — make an initial commit once you are happy with the scaffold.
- Add Actix-web and its dependencies to `Cargo.toml`: `cargo add actix-web tokio --features tokio/full`.
- Set up the Android app separately in Android Studio (Kotlin + Jetpack Compose) — bootstrapper does not scaffold it.
- Run `cargo audit` after adding dependencies to check for known vulnerabilities in the Rust backend.
- Review `hints` above for CI/CD (`github-actions`, `auto-deploy-on-merge`) and deployment (`fly`) — wire these up manually for now.
