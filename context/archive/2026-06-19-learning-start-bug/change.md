---
change_id: learning-start-bug
created: 2026-06-19
updated: 2026-06-19
archived_at: 2026-06-19T18:57:23Z
status: archived
---

# learning-start-bug

Intermittent bug: playback sometimes does not start after entering the Learning screen.

## Goal

Fix the intermittent failure where entering the Learning screen results in a blank/silent state — no audio, no phase changes, no visible progress.

## Context

Research identified two root causes: (1) an unnecessary second API call in `startSession()` that can fail silently, and (2) TTS initialization without a timeout guard. The primary fix is to eliminate the redundant `collectionRepo.getAll()` call by passing the already-available `CollectionDto` from the composable into `startSession()`.
