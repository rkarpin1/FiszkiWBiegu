# Material Design 3 Compatibility — Plan implementacji

## Przegląd

Aplikacja używa własnego systemu `LocalFiszkiColors` zamiast `MaterialTheme.colorScheme` — `ColorScheme` w `FiszkiThemedScreen` ma wypełnione tylko 6 z 27 tokenów MD3. Cel: zmapować kolory Dawn Run na pełny MD3 `ColorScheme`, dodać system kształtów MD3, a w ekranach i komponentach zastąpić `LocalFiszkiColors` przez `MaterialTheme.colorScheme` wszędzie tam, gdzie istnieje bezpośredni odpowiednik.

## Analiza stanu obecnego

- `theme/Color.kt` — paleta Dawn Run (Bg, Cream, Ember, Peach, Ink, alphas). Brak struktury MD3 tonal palette.
- `theme/Theme.kt` — `FiszkiColors` ma 12 semantycznych tokenów. `FiszkiThemedScreen` tworzy `darkColorScheme`/`lightColorScheme` z 6 tokenami (primary, onPrimary, background, onBackground, surface, onSurface). Brak: secondary, tertiary, error, surfaceVariant, onSurfaceVariant, outlineVariant i 17 innych.
- `ui/components/` — `Components.kt`, `MediaControls.kt`, `LangSelect.kt` używają `LocalFiszkiColors`; hardkodowane `RoundedCornerShape(Ndp)`.
- 7 ekranów — każdy otwiera `val c = LocalFiszkiColors.current` i używa `c.surface`, `c.text`, `c.mute` itd.
- `MaterialTheme.shapes` nie jest ustawiony (używa domyślnych MD3 shapes).

## Pożądany stan końcowy

Po zakończeniu:
- `darkColorScheme`/`lightColorScheme` ma wypełnione wszystkie tokeny MD3 używane przez komponenty Compose.
- Ekrany i komponenty używają `MaterialTheme.colorScheme.surface`, `.onSurface`, `.onSurfaceVariant`, `.outlineVariant`, `.primary`, `.onPrimary` itd. bezpośrednio.
- `LocalFiszkiColors` pozostaje jako rozszerzenie tylko dla tokenów bez odpowiednika MD3: `mute2`, `accentSoft`, `textInv`, `dark` (surface3 migruje do `scheme.surfaceVariant`).
- `MaterialTheme.shapes` zawiera tokeny dopasowane do obecnych wartości hardkodowanych.
- Kompilacja bez błędów: `./gradlew :shared:compileDebugKotlinAndroid`.
- Ręczny przegląd APK: wszystkie ekrany wyglądają identycznie jak przed migracją.

### Kluczowe odkrycia:

- `theme/Theme.kt:86–103` — miejsce gdzie rozszerzamy ColorScheme (w `FiszkiThemedScreen`)
- Mapowanie FiszkiColors → MD3: `surface→background`, `text→onBackground`, `mute→onSurfaceVariant`, `line→outlineVariant`, `surface2→surface (już ustawione w Theme.kt:93)`, `surface3→surfaceVariant`, `accent→primary`, `onAccent→onPrimary`
- Tokeny bez odpowiednika MD3 (zostają w `LocalFiszkiColors`): `mute2`, `accentSoft`, `textInv`, `dark`
- Hardkodowane kształty w bazie: `8.dp` (small), `12.dp` (medium), `18–19.dp` (large), `28.dp` (extraLarge), `50%` (full)

## Czego NIE robimy

- Nie upgradeujemy wersji `material3` z `1.11.0-alpha07`.
- Nie zmieniamy palety kolorów Dawn Run — kolory pozostają te same, tylko API dostępu się zmienia.
- Nie usuwamy `LocalFiszkiColors` — zostaje dla tokenów bez odpowiednika MD3.
- Nie dodajemy elevation system ani MD3 motion/animation tokens.
- Nie migrujemy `androidApp/` (LearningService, LearningNotificationProvider) — używają tylko FlashcardDto, nie UI.

## Podejście do implementacji

Trzy fazy sekwencyjne: najpierw fundament (Theme.kt), potem komponenty, potem ekrany. Każda faza kończy się weryfikacją kompilacji. Faza 3 kończy się ręcznym przeglądem APK.

Zasada migracji: w każdym pliku zastępujemy `val c = LocalFiszkiColors.current` zachowując linię, ale dodając `val scheme = MaterialTheme.colorScheme`. Następnie zamieniamy `c.surface` → `scheme.background`, `c.text` → `scheme.onBackground` itd. Tylko tokeny bez odpowiednika pozostają jako `c.mute2`, `c.surface3` itd.

## Faza 1: Fundamenty motywu

### Przegląd

Rozszerz ColorScheme o brakujące tokeny MD3. Zdefiniuj MaterialTheme.shapes. Nie dotykamy jeszcze ekranów — tylko warstwa motywu.

### Wymagane zmiany:

#### 1. Rozszerzenie ColorScheme

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Theme.kt`

**Cel**: Dodanie brakujących tokenów MD3 do `darkColorScheme()` i `lightColorScheme()` w `FiszkiThemedScreen`, tak żeby komponenty Compose miały pełen `MaterialTheme.colorScheme` do dyspozycji.

**Kontrakt**: Rozszerz oba wywołania (ciemny i jasny) o tokeny:
- `secondary = Peach` / `secondary = Ember2`, `onSecondary = Ink`
- `secondaryContainer = Bg3` / `secondaryContainer = Cream2`, `onSecondaryContainer = Cream` / `Ink`
- `tertiary = Color(0xFF7B68EE)` (medium slate blue z palety accentów), `onTertiary = Color.White`
- `error = Color(0xFFCF6679)` / `Color(0xFFB00020)`, `onError = Color.Black` / `Color.White`
- `surfaceVariant = Bg3` / `Cream2`, `onSurfaceVariant = MuteD` / `MuteL`
- `outline = LineD` / `LineL`, `outlineVariant = LineD` / `LineL`
- `inverseSurface = Cream` / `Bg`, `inverseOnSurface = Ink` / `Cream`

#### 2. Shapes MD3

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Theme.kt`

**Cel**: Ustawienie `MaterialTheme.shapes` z wartościami odpowiadającymi obecnym hardkodowanym kształtom, tak żeby komponenty i ekrany mogły używać `MaterialTheme.shapes.small` itd. zamiast `RoundedCornerShape(Ndp)`.

**Kontrakt**: Dodaj `shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(28.dp))` do wywołania `MaterialTheme(...)` w `FiszkiThemedScreen`. Import: `androidx.compose.material3.Shapes`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja shared: `./gradlew :shared:compileDebugKotlinAndroid` bez błędów

#### Weryfikacja ręczna:

- Theme.kt zawiera kompletne ColorScheme (dark + light) z wszystkimi wymaganymi tokenami
- `MaterialTheme.shapes` jest zdefiniowany w `FiszkiThemedScreen`

---

## Faza 2: Migracja komponentów

### Przegląd

Migracja `ui/components/` z `LocalFiszkiColors` na `MaterialTheme.colorScheme` oraz zamiana hardkodowanych kształtów na `MaterialTheme.shapes`.

### Wymagane zmiany:

#### 1. Components.kt

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/Components.kt`

**Cel**: Zastąpienie `val c = LocalFiszkiColors.current` przez `val scheme = MaterialTheme.colorScheme` dla standardowych tokenów; zachowanie `LocalFiszkiColors` tylko dla `mute2`, `surface3`.

**Kontrakt**: Dla każdego composable w pliku: dodaj `val scheme = MaterialTheme.colorScheme` obok `val c = LocalFiszkiColors.current`; zamień `c.surface` → `scheme.background`, `c.text` → `scheme.onBackground`, `c.mute` → `scheme.onSurfaceVariant`, `c.line` → `scheme.outlineVariant`, `c.accent` → `scheme.primary`, `c.onAccent` → `scheme.onPrimary`. Hardkodowane `RoundedCornerShape(Ndp)` → `MaterialTheme.shapes.small/medium/large/extraLarge` wg mapowania z fazy 1.

#### 2. MediaControls.kt

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/MediaControls.kt`

**Cel**: Ta sama migracja tokenów kolorów i kształtów.

#### 3. LangSelect.kt

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/LangSelect.kt`

**Cel**: Ta sama migracja tokenów kolorów i kształtów.

#### 4. Flag.kt — kształt proporcjonalny, bez migracji

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/Flag.kt`

**Cel**: Plik używa `RoundedCornerShape(size * 0.18f)` — proporcjonalny kształt zależny od dynamicznego parametru `size`. Nie mapuje się na żaden stały token `MaterialTheme.shapes`. Zostaw bez zmian; nie używa `LocalFiszkiColors`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja: `./gradlew :shared:compileDebugKotlinAndroid` bez błędów

#### Weryfikacja ręczna:

- Komponenty (chip, media controls, language selector) wyglądają identycznie jak przed migracją

---

## Faza 3: Migracja ekranów

### Przegląd

Migracja wszystkich 7 ekranów z `LocalFiszkiColors` na `MaterialTheme.colorScheme` dla standardowych tokenów. `LocalFiszkiColors` zostaje tylko dla `mute2`, `surface3`, `accentSoft`, `textInv`.

### Wymagane zmiany:

Dla każdego ekranu ta sama zasada:
- Dodaj `val scheme = MaterialTheme.colorScheme` obok `val c = LocalFiszkiColors.current`
- Zamień tokeny wg mapowania
- Zamień `RoundedCornerShape(Ndp)` → `MaterialTheme.shapes.*`
- Usuń `val c = LocalFiszkiColors.current` jeśli nie pozostało żadne użycie `c.*`

#### 1. CollectionsScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/collections/CollectionsScreen.kt`

#### 2. CollectionFormScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/collections/CollectionFormScreen.kt`

#### 3. FlashcardsScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/FlashcardsScreen.kt`

#### 4. CardFormScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/CardFormScreen.kt`

#### 5. LearningScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/learning/LearningScreen.kt`

#### 6. LoginScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/login/LoginScreen.kt`

#### 7. ProfileScreen.kt
**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/profile/ProfileScreen.kt`

**Cel dla każdego**: Zastąpić standardowe tokeny `LocalFiszkiColors` przez `MaterialTheme.colorScheme`; zamienić hardkodowane kształty na `MaterialTheme.shapes.*`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja: `./gradlew :shared:compileDebugKotlinAndroid` bez błędów
- APK debug buduje się: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Wszystkie 7 ekranów wyglądają identycznie jak przed migracją (brak regresji wizualnych)
- Dark mode działa poprawnie na wszystkich ekranach
- Formularze, dialogi, listy — brak artefaktów wizualnych

---

## Referencje

- Theme: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Theme.kt`
- Colors: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Color.kt`
- Libs versions: `apps/frontend/gradle/libs.versions.toml:25` (`material3 = "1.11.0-alpha07"`)

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Fundamenty motywu

#### Automatyczne

- [x] 1.1 `./gradlew :shared:compileDebugKotlinAndroid` bez błędów po rozszerzeniu ColorScheme i shapes — 4f6a1ae

#### Ręczne

- [x] 1.2 Theme.kt zawiera kompletne ColorScheme (dark + light) z wszystkimi wymaganymi tokenami — 4f6a1ae
- [x] 1.3 `MaterialTheme.shapes` zdefiniowany w FiszkiThemedScreen — 4f6a1ae

### Faza 2: Migracja komponentów

#### Automatyczne

- [x] 2.1 `./gradlew :shared:compileDebugKotlinAndroid` bez błędów po migracji komponentów

#### Ręczne

- [x] 2.2 Komponenty wyglądają identycznie jak przed migracją

### Faza 3: Migracja ekranów

#### Automatyczne

- [ ] 3.1 `./gradlew :shared:compileDebugKotlinAndroid` bez błędów
- [ ] 3.2 `./gradlew :androidApp:assembleDebug` bez błędów

#### Ręczne

- [ ] 3.3 Wszystkie 7 ekranów — brak regresji wizualnych (przegląd APK na urządzeniu)
- [ ] 3.4 Dark mode działa poprawnie na wszystkich ekranach
- [ ] 3.5 Formularze, dialogi, listy — brak artefaktów wizualnych
