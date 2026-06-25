---
change_id: collection-refresh-hang
created: 2026-06-25
updated: 2026-06-25
status: archived
archived_at: 2026-06-25T14:46:53Z
---

# collection-refresh-hang

Sporadycznie po dodaniu nowej fiszki ekran kolekcji (FlashcardsScreen) „zawisa" — widać, że kolekcja się odświeża (spinner), ale trwa to „wiecznie".

## Goal

Zdiagnozować i usunąć przyczynę nieskończonego stanu ładowania na `FlashcardsScreen` po dodaniu fiszki, tak aby zawieszone żądanie sieciowe kończyło się błędem (z istniejącym snackbarem „Ponów") zamiast wiecznego spinnera.

## Context

Badanie (`research.md`) ustaliło: po `createCard()` uruchamiane jest `loadFlashcards()`, które ustawia `isLoading = true` i resetuje je wyłącznie wewnątrz `.fold()`. Klient Ktor nie ma skonfigurowanego żadnego timeoutu (`HttpTimeout`), więc zawieszony `GET /flashcards` (np. cold-start Render lub martwe połączenie keep-alive) nigdy nie wraca → `isLoading` zostaje `true` na zawsze. Hipotezę o anulowaniu coroutine przez nawigację wykluczono — `CardFormScreen` i `FlashcardsScreen` współdzielą tę samą instancję `FlashcardsViewModel` (Activity ViewModelStore).
