# Pull-to-refresh (lista kolekcji + ekran kolekcji) — Plan implementacji

## Przegląd

Dodajemy odświeżanie gestem przeciągnięcia w dół na dwóch ekranach: `CollectionsScreen` (główny ekran / lista kolekcji) i `FlashcardsScreen` (ekran pojedynczej kolekcji). Wykorzystujemy `PullToRefreshBox` z Compose Material3 oraz nowe pole stanu `isRefreshing` w każdym `UiState`, niezależne od istniejącego `isLoading` — dzięki temu podczas odświeżania widoczna treść (lista) nie znika i nie pojawia się podwójny wskaźnik.

## Analiza stanu obecnego

- **CollectionsScreen** (`screens/collections/CollectionsScreen.kt`): `Scaffold` → `Box(fillMaxSize)` → `Column`(nagłówek `Row` + `LazyColumn(fillMaxSize)`, `:168`). Pierwsze ładowanie pokazuje wyśrodkowany `CircularProgressIndicator` przy `uiState.isLoading` (`:246`). Akcja odświeżenia istnieje jako `onRetry = { viewModel.loadCollections() }` (`:81`), błędy → snackbar „Ponów" (`:253`).
- **FlashcardsScreen** (`screens/flashcards/FlashcardsScreen.kt`): `Scaffold` → `Box(fillMaxSize().padding)` → `LazyColumn(fillMaxSize)` (`:170`). Spinner ładowania to **inline item zastępujący listę** przy `uiState.isLoading` (`:365`). Akcja `onLoadFlashcards = { viewModel.loadFlashcards() }`, błędy → snackbar „Ponów" (`:397`).
- **ViewModele**: `CollectionsViewModel.loadCollections()` (`:34-58`) i `FlashcardsViewModel.loadFlashcards()` (`:34-56`) — oba ustawiają `isLoading=true` i resetują w `finally` (po zmianie `collection-refresh-hang`).
- **Nagłówek na ekranie kolekcji**: obiekt `collection` w `FlashcardsScreen` pochodzi z cache `CollectionsViewModel` (`App.kt:206-209` — `collectionsState.collections.find { it.id == route.collection.id } ?: route.collection`). `POSTĘP` jest już liczony lokalnie z fiszek, ale `CZAS` / „ostatnio" / nazwa pochodzą z obiektu kolekcji.
- **Brak istniejącego pull-to-refresh** w projekcie. `material3 = 1.11.0-alpha07` (Compose MP) udostępnia `androidx.compose.material3.pulltorefresh.PullToRefreshBox` (API experimental → wymaga `@OptIn(ExperimentalMaterial3Api::class)`).
- **NetworkChecker**: `FlashcardsScreen` używa `isOnline` (`NetworkChecker`), `CollectionsScreen` nie.

## Pożądany stan końcowy

Na obu ekranach, gdy lista jest przewinięta na górę, przeciągnięcie w dół uruchamia odświeżenie: pojawia się standardowy wskaźnik Material3, lista pozostaje widoczna, a po zakończeniu wskaźnik znika. Na ekranie kolekcji odświeżane są zarówno fiszki, jak i statystyki nagłówka (CZAS / „ostatnio" / nazwa). Przy braku sieci gest kończy się błędem → istniejący snackbar „Ponów", a wskaźnik znika (timeouty HTTP już istnieją).

Weryfikacja: kompilacja `:shared` przechodzi; ręcznie — gest działa na obu ekranach zgodnie z powyższym.

### Kluczowe odkrycia:

- `PullToRefreshBox` musi być kontenerem przewijanej treści (owija `LazyColumn`), wskaźnik renderuje na górze swojego obszaru — `CollectionsScreen.kt:168`, `FlashcardsScreen.kt:170`.
- Osobny `isRefreshing` jest konieczny: reuse `isLoading` ukryłby listę fiszek (inline spinner, `FlashcardsScreen.kt:365`) i dał podwójny wskaźnik na liście kolekcji (`CollectionsScreen.kt:246`).
- `FlashcardsScreen` nie ma referencji do `CollectionsViewModel`; odświeżenie nagłówka trzeba wpiąć z `App.kt`, gdzie `collectionsVm` jest w zasięgu (`App.kt:206-225`).

## Czego NIE robimy

- Nie dodajemy pull-to-refresh na `ProfileScreen`, `LearningScreen`, `CardFormScreen`.
- Nie blokujemy gestu w trybie offline (świadomie: gest kończy się błędem + „Ponów").
- Nie tworzymy tematyzowanego/own-stylu wskaźnika — używamy domyślnego Material3.
- Nie zmieniamy istniejącej logiki `isLoading` (pierwsze ładowanie / usuwanie) ani snackbarów błędów.
- Nie dodajemy auto-refreshu, throttlingu ani debounce poza tym, co daje sam `PullToRefreshBox`.

## Podejście do implementacji

Dla każdego ekranu: (1) dodać `isRefreshing: Boolean = false` do `UiState`; (2) dodać `refresh()` do ViewModelu, które wykonuje to samo pobranie co funkcja ładująca, ale przełącza `isRefreshing` zamiast `isLoading` (reset w `finally`); (3) owinąć `LazyColumn` w `PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = …)`. Na ekranie kolekcji `onRefresh` dodatkowo wywołuje odświeżenie danych kolekcji wpięte z `App.kt`. Każdy ekran realizowany w osobnej, samodzielnie testowalnej fazie.

## Faza 1: Lista kolekcji (główny ekran)

### Przegląd

Pull-to-refresh na `CollectionsScreen` odświeżający listę kolekcji przez `CollectionsViewModel`.

### Wymagane zmiany:

#### 1. Stan odświeżania w CollectionsViewModel

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/collections/CollectionsViewModel.kt`

**Cel**: Umożliwić odświeżanie inicjowane gestem, niezależne od spinnera pierwszego ładowania, tak by lista nie znikała.

**Kontrakt**: Dodać `val isRefreshing: Boolean = false` do `CollectionsUiState`. Dodać `fun refresh()` wykonujące to samo pobranie co `loadCollections()` (ten sam `repo.getAll()` i aktualizacja `collections` / `lastStudiedCollection`), ale przełączające `isRefreshing` (true na starcie, `false` w `finally`) zamiast `isLoading`; nie dotyka `isLoading`. Logikę pobrania współdzielić z `loadCollections()` (np. prywatna funkcja z flagą), bez zmiany zachowania `loadCollections()`.

#### 2. PullToRefreshBox w CollectionsScreen

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/collections/CollectionsScreen.kt`

**Cel**: Owinąć listę w `PullToRefreshBox`, aby gest w dół wywoływał odświeżenie, a wskaźnik pojawiał się nad listą bez ukrywania treści.

**Kontrakt**: `CollectionsScreen` przekazuje `onRefresh = { viewModel.refresh() }` oraz `isRefreshing = uiState.isRefreshing` do `CollectionsScreenContent`. W `CollectionsScreenContent` owinąć `LazyColumn` (`:168`) w `PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh)`; `LazyColumn` pozostaje treścią boxa. Pozostawić bez zmian: wyśrodkowany `CircularProgressIndicator` przy `isLoading` (pierwsze ładowanie) i snackbar błędu. Dodać import `androidx.compose.material3.pulltorefresh.PullToRefreshBox` oraz `@OptIn(ExperimentalMaterial3Api::class)` na composable używających API.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Kompilacja + testy shared przechodzą: `./gradlew :shared:testAndroidHostTest` (z `apps/frontend`)

#### Weryfikacja ręczna:

- [ ] Na liście kolekcji przeciągnięcie w dół (lista u góry) pokazuje wskaźnik i odświeża dane
- [ ] Podczas odświeżania lista pozostaje widoczna (brak podwójnego spinnera, brak znikania treści)
- [ ] Po zakończeniu wskaźnik znika; przy braku sieci pojawia się snackbar „Ponów", a wskaźnik znika
- [ ] Pierwsze ładowanie (pusta lista) nadal pokazuje dotychczasowy wyśrodkowany spinner

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 2.

---

## Faza 2: Ekran kolekcji

### Przegląd

Pull-to-refresh na `FlashcardsScreen` odświeżający listę fiszek oraz statystyki nagłówka kolekcji.

### Wymagane zmiany:

#### 1. Stan odświeżania w FlashcardsViewModel

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/FlashcardsViewModel.kt`

**Cel**: Odświeżanie listy fiszek inicjowane gestem, niezależne od `isLoading`.

**Kontrakt**: Dodać `val isRefreshing: Boolean = false` do `FlashcardsUiState`. Dodać `fun refresh()` wykonujące to samo pobranie co `loadFlashcards()`, ale przełączające `isRefreshing` (true na starcie, `false` w `finally`) zamiast `isLoading`. Współdzielić logikę pobrania z `loadFlashcards()` bez zmiany jego zachowania.

#### 2. Wpięcie odświeżania nagłówka kolekcji z App.kt

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Pull na ekranie kolekcji ma też odświeżać dane kolekcji (CZAS / „ostatnio" / nazwa), które pochodzą z `CollectionsViewModel`, nie z `FlashcardsViewModel`.

**Kontrakt**: Przekazać do `FlashcardsScreen` nowy callback `onRefreshCollection: () -> Unit = {}`, w `entry<Route.Flashcards>` ustawiony na `{ collectionsVm.loadCollections() }`. (`collectionsVm` jest już w zasięgu, `App.kt:206-225`.)

#### 3. PullToRefreshBox w FlashcardsScreen

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/FlashcardsScreen.kt`

**Cel**: Owinąć listę w `PullToRefreshBox`; gest odświeża fiszki i nagłówek; wskaźnik sterowany stanem odświeżania fiszek.

**Kontrakt**: Dodać parametr `onRefreshCollection: () -> Unit = {}` do `FlashcardsScreen`. Przekazać `isRefreshing = uiState.isRefreshing` oraz `onRefresh = { viewModel.refresh(); onRefreshCollection() }` do `FlashcardsScreenContent`. W treści owinąć `LazyColumn` (`:170`) w `PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh)`. Pozostawić inline spinner przy `isLoading` (pierwsze ładowanie) i snackbar błędu. Dodać import `PullToRefreshBox` i `@OptIn(ExperimentalMaterial3Api::class)`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Kompilacja + testy shared przechodzą: `./gradlew :shared:testAndroidHostTest` (z `apps/frontend`)

#### Weryfikacja ręczna:

- [ ] Na ekranie kolekcji przeciągnięcie w dół pokazuje wskaźnik i odświeża listę fiszek
- [ ] Statystyki nagłówka (CZAS / „ostatnio" / nazwa) odświeżają się po geście (po zmianie danych kolekcji)
- [ ] Podczas odświeżania lista fiszek pozostaje widoczna (nie znika jak przy `isLoading`)
- [ ] Przy braku sieci gest kończy się błędem → snackbar „Ponów"; wskaźnik znika

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie.

---

## Strategia testowania

### Testy jednostkowe:

- Brak nowych testów jednostkowych (projekt nie testuje ViewModeli/Compose; jedyne testy KMP to `SrsEngineTest`). Weryfikacja przez kompilację + testy ręczne.

### Kroki testowania ręcznego:

1. Lista kolekcji: przewiń na górę, przeciągnij w dół → wskaźnik + odświeżenie; lista nie znika.
2. Ekran kolekcji: dodaj/odśwież w innym miejscu, wróć, przeciągnij w dół → fiszki i nagłówek aktualne.
3. Tryb samolotowy: przeciągnij w dół na obu ekranach → po timeout/błędzie snackbar „Ponów", wskaźnik znika.
4. Pierwsze wejście (puste/po starcie): potwierdź, że dotychczasowe spinnery `isLoading` działają jak wcześniej.

## Uwagi dotyczące wydajności

Gest na ekranie kolekcji wywołuje dwa równoległe żądania (fiszki + kolekcje) — akceptowalne; oba mają timeouty HTTP. Brak innych implikacji.

## Referencje

- Powiązane badania: `context/changes/collection-refresh-hang/research.md` (timeouty HTTP, stan `isLoading`/`isRefreshing`, współdzielenie ViewModeli)
- Wzorzec stanu ładowania: `CollectionsViewModel.kt:34-58`, `FlashcardsViewModel.kt:34-56`
- Punkty owinięcia: `CollectionsScreen.kt:168`, `FlashcardsScreen.kt:170`
- Wpięcie nawigacji/kolekcji: `App.kt:206-225`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku. Nie zmieniaj nazw tytułów kroków. Zobacz `references/progress-format.md`.

### Faza 1: Lista kolekcji (główny ekran)

#### Automatyczne

- [x] 1.1 Kompilacja + testy shared przechodzą: `./gradlew :shared:testAndroidHostTest` — d8414cb

#### Ręczne

- [x] 1.2 Przeciągnięcie w dół na liście kolekcji pokazuje wskaźnik i odświeża dane — d8414cb
- [x] 1.3 Podczas odświeżania lista pozostaje widoczna (brak podwójnego spinnera/znikania) — d8414cb
- [x] 1.4 Po zakończeniu wskaźnik znika; offline → snackbar „Ponów" i wskaźnik znika — d8414cb
- [x] 1.5 Pierwsze ładowanie (pusta lista) nadal pokazuje wyśrodkowany spinner — d8414cb

### Faza 2: Ekran kolekcji

#### Automatyczne

- [x] 2.1 Kompilacja + testy shared przechodzą: `./gradlew :shared:testAndroidHostTest`

#### Ręczne

- [x] 2.2 Przeciągnięcie w dół na ekranie kolekcji odświeża listę fiszek
- [x] 2.3 Statystyki nagłówka (CZAS / „ostatnio" / nazwa) odświeżają się po geście
- [x] 2.4 Podczas odświeżania lista fiszek pozostaje widoczna
- [x] 2.5 Offline → snackbar „Ponów"; wskaźnik znika
