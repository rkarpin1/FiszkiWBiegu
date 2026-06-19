---
date: 2026-06-19T12:00:00Z
researcher: Robert Karpiński / Claude Sonnet 4.6
git_commit: dca4ae3
branch: MVP
repository: FiszkiWBiegu
topic: "Intermittent: playback does not start after entering Learning screen"
tags: [research, LearningService, LearningViewModel, startSession, TTS, AndroidLearningController]
status: complete
last_updated: 2026-06-19
last_updated_by: Claude Sonnet 4.6
---

# Research: Intermittent — playback does not start after entering Learning screen

**Date**: 2026-06-19  
**Git Commit**: dca4ae3  
**Branch**: MVP  
**Repository**: FiszkiWBiegu

## Research Question

"Czasami nie startuje odgrywanie po wejściu do okna nauka" — intermittently, entering the Learning screen produces no audio, no phase changes; the service appears not to start.

## Summary

Two independent root causes identified, one primary and one secondary:

**Primary (highest probability)**: `LearningViewModel.startSession()` makes TWO sequential API calls. If the second call (`collectionRepo.getAll()`) fails, or if `find { it.id == collectionId }` returns `null`, `controller.start()` is **never called** — no error is shown, no retry, the screen simply stays blank. This second call is structurally unnecessary because the `CollectionDto` is already available in `LearningScreen` and only its `sourceLanguage`/`targetLanguage` fields are used by `LearningService`.

**Secondary (lower probability)**: `while (!ttsReady) delay(100ms)` in `startPlayJob()` has no timeout. If TTS initialization fails (engine unavailable, language data missing), the coroutine loops forever and playback never begins.

A third finding — `setPlaying(true)` in `TtsPlayer` does NOT trigger `onPlayWhenReadyChanged` — is not a bug: `startPlayJob()` is the authoritative start path in `ACTION_START`, the callback is only for external MediaController commands (notification buttons).

## Detailed Findings

### Finding 1 — Double API call in `startSession()` (PRIMARY)

**File**: [`apps/frontend/shared/.../screens/learning/LearningViewModel.kt:20-33`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningViewModel.kt#L20)

```kotlin
fun startSession() {
    viewModelScope.launch {
        repo.getAll(collectionId).onSuccess { flashcards ->      // API call #1
            if (flashcards.isNotEmpty()) {
                collectionRepo.getAll().onSuccess { collections ->  // API call #2 — UNNECESSARY
                    val collection = collections.find { it.id == collectionId }
                    if (collection != null) {                       // silent guard
                        controller.start(collection, flashcards)    // ONLY call path
                    }
                }
            }
        }
    }
}
```

`controller.start()` is NOT called in three scenarios — all silent (no `onFailure` anywhere in the chain):

| Scenario | Trigger |
|---|---|
| API call #1 fails | Network error, 401, timeout on `/collections/{id}/flashcards` |
| API call #2 fails | Network error, 401, timeout on `/collections` |
| `find` returns null | Race: collections list refreshed concurrently, inconsistent cache |

The second API call exists only to obtain the `CollectionDto` for language codes (`sourceLanguage`, `targetLanguage`). The composable **already has this object**:

```kotlin
// LearningScreen.kt:59-62
fun LearningScreen(
    collection: CollectionDto,   // ← already here
    viewModel: LearningViewModel = koinViewModel(key = collection.id) { parametersOf(collection.id) },
```

**Fix**: Add `collection: CollectionDto` parameter to `startSession()` and pass it from the composable. Eliminates API call #2 and the `find` guard.

### Finding 2 — TTS polling without timeout (SECONDARY)

**File**: [`apps/frontend/androidApp/.../LearningService.kt:291-295`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt#L291)

```kotlin
private fun startPlayJob() {
    playJob?.cancel()
    playJob = serviceScope.launch {
        while (!ttsReady) delay(100.milliseconds)  // ← no timeout
        playLoop()
    }
}
```

`ttsReady` is set to `true` in the TTS init callback:
```kotlin
// LearningService.kt:216
tts = TextToSpeech(this) { status -> ttsReady = status == TextToSpeech.SUCCESS }
```

If `status != SUCCESS` (TTS engine not available, language data missing, engine crash), `ttsReady` stays `false` forever. The coroutine loops indefinitely, `playLoop()` never runs, and the user sees a blank, silent screen with no indication of failure.

This is confirmed in the archive (see Historical Context below).

**Fix**: Add a timeout (~5 seconds) to the TTS polling loop; on timeout, publish an error state.

### Finding 3 — TtsPlayer.setPlaying() does NOT trigger onPlayWhenReadyChanged (NOT a bug)

**File**: [`apps/frontend/androidApp/.../TtsPlayer.kt:78-83`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt#L78)

```kotlin
fun setPlaying(playing: Boolean) {
    if (isPlayWhenReady != playing) {
        isPlayWhenReady = playing
        invalidateState()   // ← posts to Looper; handleSetPlayWhenReady called next
    }
}

override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    if (isPlayWhenReady != playWhenReady) {   // ← isPlayWhenReady already updated above
        isPlayWhenReady = playWhenReady
        onPlayWhenReadyChanged?.invoke(playWhenReady)
    }
    return Futures.immediateVoidFuture()
}
```

After `setPlaying(true)` sets `isPlayWhenReady = true`, when `handleSetPlayWhenReady(true)` fires, `true != true` is false → `onPlayWhenReadyChanged` is NOT invoked. This is correct by design: `onPlayWhenReadyChanged` bridges **external** MediaController commands (notification play/pause buttons) to `resume()`/`pause()`. Internal play control goes directly through `startPlayJob()`.

In `ACTION_START` (LearningService.kt:231-235), playback starts via:
```
isPlaying = true           // (1)
ttsPlayer.setPlaying(true) // (2) — does NOT trigger resume()
startPlayJob()             // (3) — THIS starts playLoop
```

No bug here.

### Finding 4 — speakAndWait does not validate utteranceId

**File**: [`apps/frontend/androidApp/.../LearningService.kt:365-392`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt#L365)

```kotlin
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onDone(utteranceId: String?) {           // ← utteranceId not checked against id
        if (cont.isActive) cont.resume(...)
    }
})
```

The listener receives callbacks for all utterances but does not verify `utteranceId == id`. In the expected flow (one utterance at a time, QUEUE_FLUSH) this is harmless. It becomes an issue only if stale callbacks arrive from a cancelled/replaced session — mitigated by `cont.isActive`.

Not the primary intermittent cause, but worth noting for robustness.

## Code References

- [`LearningViewModel.kt:20-33`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningViewModel.kt#L20) — `startSession()` double API call
- [`LearningScreen.kt:59-64`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningScreen.kt#L59) — `collection: CollectionDto` already available
- [`LearningService.kt:215-216`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt#L215) — `initTts()` — async init, sets `ttsReady`
- [`LearningService.kt:289-295`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt#L289) — `startPlayJob()` — `while (!ttsReady)` no timeout
- [`TtsPlayer.kt:78-83`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt#L78) — `setPlaying()` uses `invalidateState()`, not direct callback
- [`TtsPlayer.kt:41-47`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt#L41) — `handleSetPlayWhenReady()` — where `onPlayWhenReadyChanged` IS invoked (external commands only)
- [`AndroidLearningController.kt:29-37`](https://github.com/rkarpin1/FiszkiWBiegu/blob/dca4ae3/apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/AndroidLearningController.kt#L29) — `start()` sends `ACTION_START` intent

## Architecture Insights

- **LearningController** is an abstraction (`LearningController.kt:20`) — `start(collection, flashcards)` signature already accepts `CollectionDto`; the VM just needs to not re-fetch it.
- **LearningService** only uses collection for language codes (`playLoop():298-300`): `srcLang = collection?.sourceLanguage`, `tgtLang = collection?.targetLanguage`. These are immutable after collection creation.
- **`serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`** — all service coroutines run on main thread; `ttsReady` visibility is safe (same thread).
- **`tts` is non-null after `initTts()`** — `TextToSpeech()` constructor is synchronous; only the callback is async.

## Historical Context

- `context/archive/2026-05-27-s-02/plan.md:27` — explicitly flagged: "while (!ttsReady) delay(100) — jeśli TTS init zawiedzie, pętla wisi; brak timeoutu"
- `context/archive/2026-05-27-s-02/plan.md:29` — flagged: "MediaController latency — kontroler może nie być gotowy gdy user natychmiast naciska"
- `context/archive/2026-05-27-f-01/plan.md:9` — documents the silent-failure pattern in `startSession()`: "gdy offline — Result.failure jest ignorowane przez onSuccess {}, ekran nauki pokazuje CircularProgressIndicator bez końca"

## Open Questions

1. Should `startSession()` also handle the empty-flashcards case explicitly (show a message instead of silence)?
2. After the TTS timeout is added — what state should the UI show? Error toast? Retry button?
3. When entering via notification, should `startSession()` check if the service is already active (skip restarting)?
