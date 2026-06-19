---
change_id: web-crud-service
title: Serwis webowy — zarządzanie kolekcjami i fiszkami
status: preparing
created: 2026-06-19
updated: 2026-06-19
---

## Goal

Zaimplementować serwis webowy (Compose Multiplatform / WASM+JS) w ramach istniejącego modułu `webApp`, który umożliwia zarządzanie kolekcjami i fiszkami (CRUD) z logowaniem przez Google.

Brak trybu nauki — web służy wyłącznie do zarządzania treścią.

## Scope

- Logowanie Google (Google Identity Services JS API → idToken → JWT)
- Lista kolekcji (CollectionsScreen)
- Dodawanie / edycja / usuwanie kolekcji (CollectionFormScreen)
- Lista fiszek w kolekcji (FlashcardsScreen)
- Dodawanie / edycja / usuwanie fiszek (CardFormScreen)
- Obsługa wylogowania (401 → redirect do Login)

## Out of Scope

- Tryb nauki (LearningScreen) — Android only
- iOS
