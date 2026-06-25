---
starter_id: rust
package_manager: cargo
project_name: fiszki-w-biegu
hints:
  language_family: multi
  team_size: solo
  deployment_target: self-hosted
  ci_provider: github-actions
  ci_default_flow: build-and-push-binary-to-deploy-endpoint
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
---

## Why this stack

Solo developer building FiszkiWBiegu — a hands-free flashcard audio app for runners.
The project is two components: a Rust/Actix-web backend API (cloud storage for flashcards,
OAuth Google auth via Supabase Auth; PostgreSQL via Supabase + sqlx) in `apps/backend/`, and a
Kotlin Multiplatform frontend (Kotlin 2.4.0 + Compose Multiplatform 1.11.1) in `apps/frontend/`. The KMP frontend targets Android (foreground
audio service, MediaSession headphone control for learning mode) and Web (flashcard
management UI) from a single shared Kotlin codebase; iOS is not configured in MVP.
No single registry starter covers this combination; `rust` binary crate is the bootstrapper
entry point for the backend, with actix-web added manually after scaffolding. The KMP
frontend is set up separately in Android Studio. quality_override is set because Actix-web
is not in the registry and the KMP frontend has no bootstrapper support.
