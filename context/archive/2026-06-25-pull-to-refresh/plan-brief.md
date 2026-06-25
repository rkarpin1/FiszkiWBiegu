# Pull-to-refresh (lista kolekcji + ekran kolekcji) — Krótki plan

> Pełny plan: `context/changes/pull-to-refresh/plan.md`
> Powiązane badania: `context/changes/collection-refresh-hang/research.md`

## Co i dlaczego

Dodajemy odświeżanie danych gestem przeciągnięcia w dół (pull-to-refresh) na liście kolekcji (główny ekran) i na ekranie pojedynczej kolekcji. Użytkownik potrzebuje ręcznej, intuicyjnej metody pobrania świeżych danych bez wychodzenia z ekranu.

## Punkt wyjścia

Oba ekrany używają `LazyColumn` i mają funkcje ładujące (`loadCollections` / `loadFlashcards`) ze spinnerem `isLoading` oraz snackbarem błędu „Ponów". Nie ma dziś żadnego pull-to-refresh. Timeouty HTTP i siatka `isLoading` zostały dodane wcześniej (`collection-refresh-hang`), więc nieudane odświeżenie kończy się błędem, nie zawisem.

## Pożądany stan końcowy

Przeciągnięcie listy w dół (gdy jest u góry) pokazuje standardowy wskaźnik Material3 i odświeża dane; widoczna treść nie znika podczas odświeżania. Na ekranie kolekcji gest odświeża też statystyki nagłówka (CZAS / „ostatnio" / nazwa). Offline → błąd + „Ponów".

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
| ------- | ----- | ------------------- | ------ |
| Model stanu | Osobny `isRefreshing` (nie reuse `isLoading`) | Treść nie znika podczas gestu, brak podwójnego spinnera | Plan |
| Zakres na ekranie kolekcji | Fiszki + statystyki nagłówka | Spójnie świeży cały ekran (nagłówek z `CollectionsViewModel`) | Plan |
| Offline | Pozwól i pokaż błąd | Zero nowej logiki, spójne z istniejącym „Ponów" + timeouty | Plan |
| Wskaźnik | Domyślny Material3 | Mniej pracy i ryzyka, spójne z Material3 | Plan |

## Zakres

**W zakresie:**
- `CollectionsScreen` (lista kolekcji) — pull-to-refresh
- `FlashcardsScreen` (ekran kolekcji) — pull-to-refresh + odświeżenie nagłówka kolekcji
- Nowe pole `isRefreshing` i `refresh()` w `CollectionsViewModel` i `FlashcardsViewModel`

**Poza zakresem:**
- Pull-to-refresh na Profile / Learning / CardForm
- Blokowanie gestu offline, tematyzowany wskaźnik, auto-refresh/throttling
- Zmiany istniejącej logiki `isLoading` i snackbarów

## Architektura / Podejście

`PullToRefreshBox` (Material3, experimental → `@OptIn(ExperimentalMaterial3Api::class)`) owija `LazyColumn` każdego ekranu. ViewModel zyskuje `isRefreshing` + `refresh()` współdzielące pobranie z funkcją ładującą, ale przełączające `isRefreshing` (reset w `finally`). Na ekranie kolekcji `onRefresh` woła `viewModel.refresh()` oraz — przez callback wpięty z `App.kt` — `collectionsVm.loadCollections()`, bo nagłówek kolekcji pochodzi z `CollectionsViewModel`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
| ---- | ------------ | --------------- |
| 1. Lista kolekcji | Pull-to-refresh na głównym ekranie | Współistnienie z wyśrodkowanym spinnerem `isLoading` |
| 2. Ekran kolekcji | Pull-to-refresh + odświeżenie nagłówka | Wpięcie odświeżania kolekcji z `App.kt`; inline spinner nie może chować listy |

**Wymagania wstępne:** brak (zmiana `collection-refresh-hang` już wdrożona).
**Szacowany nakład pracy:** ~1 sesja, 2 fazy, ~4 pliki.

## Otwarte ryzyka i założenia

- Założenie: `PullToRefreshBox` jest dostępny w `material3 1.11.0-alpha07` (Compose MP) — weryfikowane kompilacją w kryterium automatycznym.
- `collectionsVm.loadCollections()` wywoływane z ekranu kolekcji ustawia `isLoading` na niewidocznym `CollectionsScreen` — nieszkodliwe (brak widocznego spinnera).

## Kryteria sukcesu (podsumowanie)

- Gest w dół odświeża dane na obu ekranach, z widocznym wskaźnikiem i bez znikania treści.
- Na ekranie kolekcji odświeżają się też statystyki nagłówka.
- Offline kończy się snackbarem „Ponów", wskaźnik znika.
