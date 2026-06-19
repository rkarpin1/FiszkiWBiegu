---
id: srs-local-refresh
title: "Odświeżenie lokalnych danych SRS po ocenie fiszki"
status: preparing
created: 2026-06-19
updated: 2026-06-19
---

## Cel

Po wysłaniu oceny fiszki do backendu zaktualizować pola `srsLevel` i `lastStudiedAt` w lokalnym obiekcie `FlashcardDto` wewnątrz `SrsCard`, tak aby:
- `currentCard` w `LearningState` odzwierciedlał nowy poziom SRS
- `flashcards` lista (kolejka) zawierała aktualne wartości
- `SrsLevelIndicator` pokazywał poprawny `decayLevel()` na bieżącej fiszce
