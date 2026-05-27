<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Automatyczne wylogowanie przy wygaśnięciu tokenu (401 interceptor)

- **Plan**: context/changes/s-02a/plan.md
- **Scope**: All phases (Phase 1 + Phase 2)
- **Date**: 2026-05-27
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — emitUnauthorized() uses emit() instead of tryEmit()

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: AuthEventBus.kt:11
- **Detail**: `emitUnauthorized()` called suspend `emit()`, which can suspend the HTTP coroutine if buffer is full and collector is busy. Two simultaneous 401s from parallel requests could freeze the second network call indefinitely.
- **Fix A ⭐ Recommended**: Changed `emit()` → `tryEmit()`, removed `suspend` from `emitUnauthorized()`
  - Strength: `tryEmit()` is non-blocking; with `extraBufferCapacity=1` it almost always succeeds; discarding a second 401 is correct.
  - Tradeoff: Minimal — single-line change, no side effects.
  - Confidence: HIGH — `extraBufferCapacity=1` guarantees tryEmit succeeds unless a second 401 arrives within the same buffer slot.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A

### F2 — validateResponse intercepts /auth/login too

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: ApiClient.kt:29-33
- **Detail**: A 401 from `/auth/login` (bad Google token) also triggered `unauthorizedEvents`, causing `authRepository.logout()` to be called while already on LoginScreen. Not a crash, but latent bug.
- **Fix A ⭐ Recommended**: Added `!response.call.request.url.encodedPath.endsWith("/auth/login")` condition to interceptor.
  - Strength: Interceptor now only fires for authenticated endpoints — semantically clean. `/auth/login` intentionally has no `bearerAuth()`.
  - Tradeoff: Minor — one-line condition change.
  - Confidence: HIGH — consistent with the design intent.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A

### F3 — Potential double logout (manual + 401 race)

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: App.kt:39-44
- **Detail**: If user clicks Logout exactly when API returns 401, `authRepository.logout()` and `destination = Destination.Login` could be called twice. `clearToken()` is idempotent; `Destination.Login` on an already-active LoginScreen is safe.
- **Fix**: None needed — both operations are idempotent.
- **Decision**: SKIPPED — already safe

### F4 — Missing comment documenting replay=0 choice

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: AuthEventBus.kt:8
- **Detail**: `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` defaults to `replay=0` — correct for event bus, but a future reader might "fix" it by adding `replay=1`, which would cause logout on app start if an event was buffered.
- **Fix**: Added `// replay=0: new collectors must not receive past events` comment.
- **Decision**: FIXED
