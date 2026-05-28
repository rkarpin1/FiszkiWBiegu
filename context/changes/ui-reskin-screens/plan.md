# UI Reskin — Faza B: Reskin Istniejących Ekranów

## Przegląd

Przepisanie 4 istniejących ekranów (LoginScreen, CollectionsScreen, FlashcardsScreen, LearningScreen)
do designu „Dawn Run" z prototypu `.tmp/UI`, plus wprowadzenie nawigacji navigation3 z dolnym tab barem
i ProfileScreen stub. Logika ViewModeli, repozytoriów i warstwy danych — bez zmian.

## Analiza stanu obecnego

- `LoginScreen.kt` — prosty Column z tytułem i jednym przyciskiem Google Sign-In
- `CollectionsScreen.kt` — TopAppBar + LazyColumn + trzy AlertDialogi (add/edit/delete) + Snackbar
- `FlashcardsScreen.kt` — TopAppBar + LazyColumn fiszek + dwa AlertDialogi + FAB
- `LearningScreen.kt` — Scaffold + card stage z row kontrolerów prev/play-pause/next
- `App.kt` — `sealed Destination` + `var destination by remember` + `when(dest)` rendering

Gotowe z S-04-A: `FiszkiThemedScreen`, `FiszkiAppTheme`, `LocalFiszkiColors`, komponenty
(Flag, StudyChip, TrackBar, CapsLabel, MediaControls), czcionki Bricolage + JetBrains Mono.

## Pożądany stan końcowy

Po zakończeniu planu:
1. Wszystkie 4 ekrany używają designu „Dawn Run" z `FiszkiThemedScreen(naturalDark = true)`
2. App.kt renderuje za pomocą navigation3 `BackStack` + `NavDisplay`
3. Dolny `NavigationBar` (Kolekcje / Konto) widoczny tylko na ekranach Collections i Profile
4. `ProfileScreen.kt` — stub: gradient avatar, przycisk Wyloguj; email/userName = stub
5. `./gradlew :androidApp:assembleDebug` i `:shared:wasmJsMainClasses` — bez błędów
6. APK instaluje się, pełny flow (login → kolekcje → fiszki → nauka) działa bez regresji

## Kluczowe odkrycia

- `navigation3-compose` jest już w `commonMain.dependencies` (`libs.navigation3.compose`) — używane po raz pierwszy w tej fazie
- `CollectionDto` nie ma `progress`, `lastStudied`, `icon` — wszystkie stubowane wg tabeli poniżej
- Prototyp używa `LocalFiszkiTokens` z surowymi tokenami; nasz `Theme.kt` eksponuje `LocalFiszkiColors` z tokenami semantycznymi — wymagana ręczna adaptacja (tabela mapowania w sekcji poniżej)
- `TokenStorage` przechowuje tylko JWT, nie email — `ProfileScreen` w tej fazie nie może pokazać prawdziwego emaila
- `onLogout` przenosi się z `CollectionsScreen` do `ProfileScreen`; w Phase 2 App.kt wiring się zmienia

## Czego NIE robimy

- `CollectionFormScreen` / `CardFormScreen` jako osobne ekrany (S-04-C)
- Bottom-sheet redesign dialogów (S-04-C)
- Realne dane `progress`, `lastStudied`, `icon`, email, streak (S-04-D)
- Light theme na jakimkolwiek ekranie — wszystkie ciemne (`naturalDark = true`)
- Logika `Wiem`/`Nie wiem` (PRD Non-Goal — spaced repetition poza MVP scope)
- `TranslateService` / Apple / Facebook sign-in (poza MVP scope lub S-04-D)

## Podejście do implementacji

Phase 1 modyfikuje tylko pliki ekranów (`*Screen.kt`) — App.kt pozostaje bez zmian. Każdy ekran
dostaje `FiszkiThemedScreen(naturalDark = true)` zamiast `Scaffold` z domyślnym MaterialTheme.
Phase 2 przepisuje App.kt na navigation3 i dodaje `ProfileScreen.kt`.

## Krytyczne szczegóły implementacji

**Mapowanie tokenów** (prototyp `LocalFiszkiTokens` → nasze `LocalFiszkiColors`):

| Prototyp (`t.xxx`) | Nasze (`c.xxx`) |
|--------------------|-----------------|
| `t.bg`             | `c.surface`     |
| `t.bg2`            | `c.surface2`    |
| `t.bg3`            | `c.surface3`    |
| `t.cream`          | `c.text`        |
| `t.muteD`          | `c.mute`        |
| `t.muteD2`         | `c.mute2`       |
| `t.lineD`          | `c.line`        |
| `t.ember`          | `c.accent`      |
| `t.peach`          | `c.accentSoft`  |

**Zaślepki w LearningScreen:**
- `elapsed` — `var elapsedSec by remember { mutableStateOf(0) }` + `LaunchedEffect(state.isPlaying)` zwiększający co sekundę (format MM:SS)
- `speed` — `var speed by remember { mutableStateOf(1.0f) }` — lokalny state, 4 chipy wizualne, nie trafia do VM
- `Wiem` / `Nie wiem` — oba `enabled=false` (placeholder PRD Non-Goal)
- `repetition` — hardkodowane "3/3" w card stage indicator

**navigation3 w App.kt (Phase 2):**
- Route sealed interface zastępuje istniejące sealed Destination
- `rememberNavBackStack(initialRoute)` — nie wymaga serializacji tras; `CollectionDto` przekazywany jako obiekt w pamięci
- `NavigationBar` pokazywany warunkowo: `backStack.lastOrNull() is Route.Collections || backStack.lastOrNull() is Route.Profile`
- Logout: `backStack.clear(); backStack.add(Route.Login)`

---

## Faza 1: Reskin 4 ekranów

### Przegląd

Przepisanie layoutów 4 ekranów do designu Dawn Run. App.kt i ViewModele — bez zmian. Każdy ekran
dostaje `FiszkiThemedScreen(naturalDark = true)` jako root wrapper.

### Wymagane zmiany

#### 1. LoginScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/LoginScreen.kt`

**Cel**: Zastąpić prosty Column layoutem AuthScreen z prototypu — ciemne tło, brand mark (ember tile + nazwa), copy hero, 3 przyciski social.

**Kontrakt**: Sygnatura pozostaje `LoginScreen(isLoading, error, onSignInClick)`.
- Root: `FiszkiThemedScreen(naturalDark = true)` owija `Column(fillMaxSize, bg = c.surface)`
- Góra: `BrandMarkSmall` (ember 34×34dp rounded tile z 3 białymi barkami + tekst „FiszkiWBiegu")
- Hero copy: `CapsLabel("// ZACZNIJ W 5 SEKUND", color = c.accentSoft)`, `displayMedium "Wejdź\ni ruszaj."`, `bodyLarge (c.mute)` podtytuł
- `Spacer(weight(1f))` wypycha przyciski na dół
- `SocialButton` Google — `clickable` → `onSignInClick()` gdy `!isLoading`, inaczej `CircularProgressIndicator`
- `SocialButton` Apple — `enabled=false` (brak onClick)
- `SocialButton` Facebook — `enabled=false`
- `SocialButton`: `Row(60dp height, RoundedCorner 16dp, bg c.surface2, border 1dp c.line)` + ikona placeholder + `titleLarge c.text SemiBold`
- Error: jeśli `error != null` → `Text(error, color = c.accent, bodySmall)` nad przyciskami
- Brak klauzuli prawnej (MVP uproszczenie)

#### 2. CollectionsScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Zastąpić MaterialTheme TopAppBar + LazyColumn layoutem wariantu B z prototypu — dark header z pozdrowieniem, brak hero (lastUsed=null), lane rows z accentColorForId.

**Kontrakt**: Sygnatura zachowuje `onLogout` w Phase 1 (parametr nieużywany wewnętrznie — brak przycisku Wyloguj w nowym layoucie; usunięty dopiero w Phase 2 gdy App.kt jest przepisywany):
```
CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
    onLogout: () -> Unit,
)
```
App.kt pozostaje bez zmian i kompiluje się w Phase 1.

Layout:
- Root: `FiszkiThemedScreen(naturalDark = true)` → `Scaffold(containerColor = c.surface, floatingActionButton = AddFab)`
- `CollectionsHeader`: Row z `Column("CZEŚĆ!", headlineLarge c.text "Twoje kolekcje")` + streak chip hidden (streak=0 stub → chip nie wyświetlany) + brak Settings icon (S-04-C)
- Brak `LastUsedHero` (lastUsed=null) — zamiast: `Box(Modifier.fillMaxWidth().clip(RoundedCorner 24dp).background(c.surface2).border(1dp c.line).padding(22dp))` z tekstem `CapsLabel("WYBIERZ KOLEKCJĘ")` + `Text("Zacznij od wybrania kolekcji.", c.mute)`
- `CapsLabel("KOLEKCJE")` nagłówek sekcji
- `LaneRow` per kolekcja: numer `idx.padStart(2,'0')` (mono, c.mute), `Column(name headlineSmall c.text, "${flashcards.size} fiszek" — tu `uiState.collections.size` nie daje count per kolekcja, więc LaneRow pomija count: tylko nazwa + accentColorForId bar)`
  - **Uwaga**: `CollectionDto` nie ma `cards` — LaneRow nie pokazuje liczby fiszek; `TrackBar(progress=0f, accent=accentColorForId(c.id), segments=12, height=3.dp)`
  - Arrow forward icon (c.mute)
  - Divider między wierszami: `Box(1dp height, c.line)` (brak jeśli last)
- `AddFab`: 60dp cream circle z Add icon (bg)
- `AlertDialog`-i (add/edit/delete) pozostają bez zmian
- `Snackbar` error pozostaje

#### 3. FlashcardsScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/FlashcardsScreen.kt`

**Cel**: Zastąpić TopAppBar + LazyColumn layoutem CollectionDetailScreen — hero z CTA „Słuchaj w biegu", stats row stub, para językowa, lista fiszek.

**Kontrakt**: Sygnatura pozostaje `FlashcardsScreen(collection, viewModel, networkChecker, onBack, onStartLearning)`.
- Root: `FiszkiThemedScreen(naturalDark = true)` → `Scaffold(containerColor=c.surface, floatingActionButton=AddFlashcardFab)`
- Top bar: `Row(22dp padding)` z back button (`Box 40dp surface2 border rounded12 ArrowBack icon`) + `Spacer(weight1f)` + `Text("Kolekcja", capsMono c.mute)` + `Spacer(weight1f)` + ikona edycji (otwiera `EditCollectionDialog` — zachowuje istniejącą logikę)
- Hero section: `Text(collection.name, displaySmall c.text)`, `Text("PL → EN", mono c.mute)`
- Stats row: `Row` 3 kafelki w `surface2 border rounded16`: `FISZEK / ${uiState.flashcards.size}`, `POSTĘP / 0%`, `CZAS / —` (wszystko stub, capsMono + bigNumber)
- CTA: `Row(56dp ember rounded18 clickable=onStartLearning, enabled=uiState.flashcards.isNotEmpty() && isOnline)` z `Icon(Headphones)` + `Text("Słuchaj w biegu")`
  - Jeśli `!isOnline` → `Text("Brak połączenia", labelSmall c.mute)` pod przyciskiem
- Language pair: `Row(centered)` z `Flag("pl",26dp)` + `Icon(ArrowForward c.mute)` + `Flag("en",26dp)` + `CapsLabel("POLSKI → ANGIELSKI")`
- `CapsLabel("FISZKI · ${uiState.flashcards.size}")` header
- `LazyColumn` fiszek — `FlashcardItem`: numer `idx.padStart(2,'0')` (mono c.mute), `Column(polishText bodyLarge c.text, englishText bodyMedium c.mute)`, ⋯ menu (edit/delete TextButton)
- `AddFlashcardFab`: jak AddFab w collections — cream/inverted
- `FlashcardFormDialog` i `DeleteFlashcardDialog` pozostają bez zmian

#### 4. LearningScreen.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningScreen.kt`

**Cel**: Zastąpić Scaffold + prosty layout layoutem StudyScreenB — card stage z fazami PL/EN, MediaControls, zaślepione Wiem/Nie wiem.

**Kontrakt**: Sygnatura pozostaje `LearningScreen(collection, viewModel, onBack)`. `LaunchedEffect` i `DisposableEffect` bez zmian.
- Root: `FiszkiThemedScreen(naturalDark = true)` → `Column(fillMaxSize, bg=c.surface)`
- Top bar: `Row(22dp)` — back button (`Box 40dp surface2 ArrowBack`) + `Spacer(weight1f)` + timer elapsed: `Text(formattedElapsed, mono c.mute)`
  - Elapsed stub: `var elapsedSec by remember { mutableStateOf(0) }` + `LaunchedEffect(state.isPlaying) { while(isActive && state.isPlaying) { delay(1000); elapsedSec++ } }`; format: `"%02d:%02d".format(elapsedSec/60, elapsedSec%60)`
- Header: `Row(26dp)` — `Column(CapsLabel("KOLEKCJA"), Text(collection.name titleLarge c.text))` + index `"${(state.currentIndex+1).toString().padStart(2,'0')} / ${state.flashcards.size}"` (bigNumber-style mono c.accent / c.mute)
- Card stage: `Column(weight1f, 22dp horizontal, clip RoundedCorner28, bg c.surface2, border c.line, padding 28dp)`
  - Header: `Row` — `Box(10dp circle c.accent)` + `Flag("en",20dp)` + `CapsLabel("TERAZ · POWTÓRZENIE 3/3", c.accentSoft)`
  - `Spacer(14dp)`
  - `Text(card?.englishText, displayLarge c.text)` (lub `CircularProgressIndicator` gdy card==null)
  - `Spacer(weight1f)`
  - Separator: `Box(1dp c.line fillWidth)`
  - `Spacer(18dp)`
  - `Row` — `Flag("pl",16dp)` + `CapsLabel("ORYGINAŁ", c.mute2)`
  - `Text(card?.polishText, headlineMedium c.mute SemiBold)`
- Bottom section: `Column(22dp horizontal, top 18dp, bottom 18dp, spacedBy 12dp)`
  - Phase indicator: `Text(when(state.phase) { SPEAKING_POLISH → "Wymawiam po polsku..."; SPEAKING_ENGLISH → "Wymawiam po angielsku..."; IDLE → "" }, labelSmall c.accentSoft)`
  - Speed selector (stub): `Row(spacedBy 8dp)` z 4 chipami `StudyChip("0.75×")` itd.; aktywny chip: `bg=c.accent text=c.onAccent`; `var speed by remember { mutableStateOf(1.0f) }` — klik zmienia lokalny state, nie wpływa na VM
  - `MediaControls(isPlaying=state.isPlaying, onPrev=viewModel::previous, onPlayPause={if(state.isPlaying) viewModel.pause() else viewModel.play()}, onNext=viewModel::next)`
  - Wiem/Nie wiem row: `Row(spacedBy 10dp)` — oba przyciski `enabled=false`, stylistyka jak prototyp (Nie wiem: surface2 border; Wiem: c.accent)

### Kryteria sukcesu

#### Weryfikacja automatyczna

- `./gradlew :shared:wasmJsMainClasses` — BUILD SUCCESSFUL
- `./gradlew :androidApp:assembleDebug` — BUILD SUCCESSFUL (App.kt kompiluje się bez zmian)

#### Weryfikacja ręczna

- Wizualna weryfikacja każdego ekranu — dark tło, czcionki Bricolage/Mono, kolory dawn run

---

## Faza 2: App.kt — navigation3 + tab bar + ProfileScreen

### Przegląd

Przepisanie `App.kt` na navigation3 (`BackStack` + `NavDisplay`), dodanie `ProfileScreen.kt` stub,
dolny `NavigationBar`. `CollectionsScreen` nie otrzymuje już `onLogout` — wylogowanie przez ProfileScreen.

### Wymagane zmiany

#### 1. ProfileScreen.kt (nowy plik)

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ProfileScreen.kt`

**Cel**: Stub ekranu konta — avatar gradient, przycisk Wyloguj. Email/displayName = brak do S-04-D.

**Kontrakt**: `ProfileScreen(onLogout: () -> Unit)`
- Root: `FiszkiThemedScreen(naturalDark = true)` → `Column(fillMaxSize, bg=c.surface)`
- Header: `Text("Konto", headlineLarge c.text, padding 26dp)`
- Profile card: `Column(surface2 border rounded24 padding 24dp, centered)`:
  - Avatar: `Box(88dp Circle, Brush.linearGradient(c.accent, c.accentSoft))` + `Text("T", displaySmall White Bold)` (stub inicjał)
  - `Text("Ty", headlineMedium c.text)` (stub displayName)
  - Provider badge: `Row(chip-style surface3 border)` + `Text("G" White bg)` + `CapsLabel("Zalogowano przez Google", c.mute)`
- `Spacer(weight1f)`
- Wyloguj button: `Row(56dp fillWidth border rounded18 c.line)` z `Icon(ArrowForward c.mute)` + `Text("Wyloguj", titleMedium SemiBold)`
- `Spacer(18dp)`

#### 2. CollectionsScreen.kt — usunięcie `onLogout`

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/CollectionsScreen.kt`

**Cel**: Usunąć parametr `onLogout` z sygnatury (przeniesiony do ProfileScreen; App.kt w Phase 2 już go nie przekazuje).

**Kontrakt**: Zaktualizuj sygnaturę — usuń `onLogout: () -> Unit`:
```
CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
)
```

#### 3. App.kt — przepisanie na navigation3

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Zastąpić sealed Destination + `var destination` nawigacją navigation3 z BackStack/NavDisplay. Dodać NavigationBar (2 taby) pokazywany warunkowo.

**Kontrakt**:

Definicja tras (sealed interface Route w tym samym pliku):
```kotlin
private sealed interface Route {
    data object Login : Route
    data object Collections : Route
    data class Flashcards(val collection: CollectionDto) : Route
    data class Learning(val collection: CollectionDto) : Route
    data object Profile : Route
}
```

`App()` composable:
- `val initial = if (authRepository.isLoggedIn()) Route.Collections else Route.Login`
- `val backStack = rememberNavBackStack(initial)` — z `org.jetbrains.androidx.navigation3`
- `LaunchedEffect` z `authEventBus` → `backStack.clear(); backStack.add(Route.Login)`
- `val currentRoute = backStack.lastOrNull()`
- `val showTabBar = currentRoute is Route.Collections || currentRoute is Route.Profile`
- `FiszkiAppTheme(override = null) { MaterialTheme { Scaffold(bottomBar = { if (showTabBar) AppBottomBar(...) }) { NavDisplay(...) } } }`

`AppBottomBar`:
```kotlin
@Composable
private fun AppBottomBar(current: Route, onCollections: () -> Unit, onProfile: () -> Unit) {
    NavigationBar(containerColor = LocalFiszkiColors.current.surface2) {
        NavigationBarItem(
            selected = current is Route.Collections,
            onClick = onCollections,
            icon = { Icon(Icons.Default.LibraryBooks, null) },
            label = { Text("Kolekcje") },
        )
        NavigationBarItem(
            selected = current is Route.Profile,
            onClick = onProfile,
            icon = { Icon(Icons.Default.Person, null) },
            label = { Text("Konto") },
        )
    }
}
```

`NavDisplay` entries:
- `Route.Login` → `LoginScreen(isLoading, error, onSignInClick = { ... })`; po sukcesie: `backStack.clear(); backStack.add(Route.Collections)`
- `Route.Collections` → `CollectionsScreen(onCollectionClick = { backStack.add(Route.Flashcards(it)) })`
- `Route.Flashcards` → `FlashcardsScreen(collection, onBack = { backStack.removeLastOrNull() }, onStartLearning = { backStack.add(Route.Learning(it.collection)) })`
- `Route.Learning` → `LearningScreen(collection, onBack = { backStack.clear(); backStack.add(Route.Collections) })`
- `Route.Profile` → `ProfileScreen(onLogout = { authRepository.logout(); backStack.clear(); backStack.add(Route.Login) })`

Tab bar callbacks:
- `onCollections`: `if (currentRoute is Route.Profile) backStack.removeLastOrNull()` — tab bar widoczny tylko na Collections/Profile, więc ten warunek wyczerpuje wszystkie możliwości
- `onProfile`: `if (currentRoute !is Route.Profile) backStack.add(Route.Profile)`

**Uwaga implementacyjna**: Dokładne importy navigation3 (`rememberNavBackStack`, `NavDisplay`, `entryProvider`, `entry<T>`) należy zweryfikować z API biblioteki `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`. Kluczowe klasy mogą być w pakietach `androidx.navigation3.runtime` lub `org.jetbrains.androidx.navigation3.*`. BackStack jest typu list-like; `removeLastOrNull()` odpowiada cofnięciu, `add(route)` — nawigacji naprzód.

### Kryteria sukcesu

#### Weryfikacja wstępna

- Importy navigation3 (`rememberNavBackStack`, `NavDisplay`, `entryProvider`) zweryfikowane z artefaktu navigation3-ui:1.1.1

#### Weryfikacja automatyczna

- `./gradlew :androidApp:assembleDebug` — BUILD SUCCESSFUL (exit code 0)
- `./gradlew :shared:wasmJsMainClasses` — BUILD SUCCESSFUL

#### Weryfikacja ręczna

- APK instaluje się i uruchamia bez crashu
- Dolny tab bar widoczny na CollectionsScreen i ProfileScreen; ukryty na FlashcardsScreen i LearningScreen
- `ProfileScreen` wyświetla stub avatar + przycisk Wyloguj; klik Wyloguj → wraca do LoginScreen
- Pełny flow (login → kolekcje → fiszki → nauka → wróć → kolekcje) działa bez regresji
- Dialogi add/edit kolekcji i fiszek działają jak dotychczas
- Sesja nauki odtwarza fiszki przez TTS; przyciski Wiem/Nie wiem disabled

---

## Strategia testowania

### Testy automatyczne

- `./gradlew :shared:wasmJsMainClasses` i `:androidApp:assembleDebug` po Phase 1
- `./gradlew :androidApp:assembleDebug` po Phase 2 (pełna aplikacja)

### Kroki testowania ręcznego

1. Po Phase 1: zweryfikuj `shared:wasmJsMainClasses` (sprawdza kompilację bez pełnego APK)
2. Po Phase 2: zainstaluj APK, przejdź pełny flow:
   - Login Google → Collections (dark design, lane rows z accentami)
   - Dodaj kolekcję (dialog) → pojawia się w liście
   - Wejdź w kolekcję → FlashcardsScreen (hero, stats 0%, CTA, lista)
   - Dodaj fiszkę (dialog) → pojawia się w liście
   - „Słuchaj w biegu" → LearningScreen (dark card stage, timer, MediaControls)
   - Prev/Play-Pause/Next działają; TTS odtwarza; wróć → Collections
   - Tab Konto → ProfileScreen stub → Wyloguj → LoginScreen
3. Sprawdź Logcat pod kątem błędów kompilacji resources/fonts

## Referencje

- Prototyp UI: `.tmp/UI/src/ui/screens/`
- S-04-A plan (design system): `context/changes/ui-reskin-design-system/plan.md`
- navigation3 API: `libs.navigation3.compose` w build.gradle.kts shared
- PRD hard rule: wszystkie destrukcyjne akcje mają potwierdzenie (usuwanie kolekcji/fiszki — dialogi zachowane)

---

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Reskin 4 ekranów

#### Automatyczne

- [x] 1.1 `./gradlew :shared:wasmJsMainClasses` — BUILD SUCCESSFUL — 377e7aa
- [x] 1.2 `./gradlew :androidApp:assembleDebug` — BUILD SUCCESSFUL — 377e7aa

#### Ręczne

- [x] 1.3 Wizualna weryfikacja 4 ekranów w dark design (ręcznie po APK lub Compose Preview) — 377e7aa

### Faza 2: App.kt — navigation3 + tab bar + ProfileScreen

#### Automatyczne

- [x] 2.0 Weryfikacja navigation3 API — potwierdzono importy `rememberNavBackStack`, `NavDisplay`, `entryProvider` z artefaktu navigation3-ui:1.1.1
- [x] 2.1 `./gradlew :androidApp:assembleDebug` — exit code 0
- [x] 2.2 `./gradlew :shared:wasmJsMainClasses` — BUILD SUCCESSFUL

#### Ręczne

- [x] 2.3 APK instaluje się i uruchamia bez crashu
- [x] 2.4 Pełny flow (login → kolekcje → fiszki → nauka) działa bez regresji
- [x] 2.5 Tab bar widoczny na Collections/Profile; ukryty na Flashcards/Learning
- [x] 2.6 ProfileScreen stub działa; Wyloguj wraca do LoginScreen
- [ ] 2.7 Dialogi add/edit/delete dla kolekcji i fiszek działają poprawnie
