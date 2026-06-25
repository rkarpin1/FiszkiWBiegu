---
change_id: pull-to-refresh
created: 2026-06-25
updated: 2026-06-25
status: implementing
---

# pull-to-refresh

Dodać odświeżanie danych gestem przeciągnięcia w dół (pull-to-refresh) na liście kolekcji (główny ekran) i na ekranie kolekcji.

## Goal

Użytkownik może ręcznie odświeżyć dane, przeciągając listę w dół — na `CollectionsScreen` (lista kolekcji) i `FlashcardsScreen` (ekran kolekcji), bez znikania widocznej treści podczas odświeżania.

## Context

Oba ekrany używają `LazyColumn` i mają już funkcje ładujące (`loadCollections` / `loadFlashcards`) oraz obsługę błędów ze snackbarem „Ponów". Pull-to-refresh dodaje `PullToRefreshBox` (Material3) i osobny stan `isRefreshing` (niezależny od `isLoading`, by treść nie znikała). Na ekranie kolekcji gest odświeża zarówno listę fiszek, jak i statystyki nagłówka (dane kolekcji z `CollectionsViewModel`). Timeouty HTTP i siatka `isLoading` są już dodane (zmiana `collection-refresh-hang`).
