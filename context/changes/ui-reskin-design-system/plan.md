# UI Reskin — Faza A: Design System i Komponenty Bazowe

## Przegląd

Wdrożenie warstwy wizualnej z prototypu `.tmp/UI` do modułu `shared` bez żadnych zmian w logice biznesowej. Po tej fazie moduł kompiluje się z paletą „Dawn Run", czcionkami Bricolage + JetBrains Mono oraz gotowymi komponentami (Flag, TrackBar, MediaControls). Istniejące ekrany działają, ale App.kt zostaje opakowany przez `FiszkiAppTheme` — widoczna zmiana to ciemne tło w miejscach, gdzie Compose domyślnie stosuje Surface z MaterialTheme.

## Analiza stanu obecnego

- `compose.components.resources` — już w `commonMain.dependencies` w `shared/build.gradle.kts`
- `material-icons-extended` — **brak** w konfiguracji; dodajemy przez `compose.materialIconsExtended`
- katalog `ui/` i `theme/` — nie istnieją; tworzymy od zera
- `composeResources/drawable/` — istnieje (jeden plik SVG); brak podkatalogu `font/`
- wszystkie pliki z `.tmp/UI` używają pakietu `pl.fiszki.wbiegu` — wymagana zamiana na `pl.rkarpinski.fiszkiwbiegu`
- `TtsEngine`, `SocialAuthProvider`, `TranslateService` z prototypu — **poza zakresem**; projekt ma działający TTS i auth

## Pożądany stan końcowy

Po wykonaniu planu:
1. Katalogi `theme/` i `ui/components/` istnieją w `commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/`
2. Pięć plików czcionek `.ttf` jest w `commonMain/composeResources/font/`
3. `App.kt` jest opakowany przez `FiszkiAppTheme(override = null)` — existing screens nie crashują
4. `./gradlew :androidApp:assembleDebug` kończy się sukcesem
5. `./gradlew :webApp:wasmJsBrowserDevelopmentRun` kompiluje się bez błędów

### Kluczowe odkrycia

- `compose.materialIconsExtended` jest dostępny przez type-safe accessor Compose Multiplatform plugin (CMP 1.11.0) bez dodawania wpisu do `libs.versions.toml`
- `FiszkiAppTheme(override = null)` to czysty `CompositionLocalProvider` — nie duplikuje `MaterialTheme`, jest bezpieczne do wstawienia przed tym jak ekrany zaczną używać `FiszkiThemedScreen`
- `accentColorForId(id: String)` trafia do `theme/Color.kt` jako wolnostojąca funkcja; unikamy zanieczyszczenia DTO importem `Color`
- Canvas API w `Flag.kt` działa natywnie w Compose Multiplatform na Androidzie i WasmJS

## Czego NIE robimy

- Żadnych zmian w istniejących ekranach (`LoginScreen`, `CollectionsScreen`, `FlashcardsScreen`, `LearningScreen`) — to faza B
- Brak `TtsEngine`, `SocialAuthProvider`, `TranslateService` z prototypu — projekt ma działające implementacje
- Brak modyfikacji `data/` ani ViewModeli
- Brak nowych nawigacji ani ekranów

## Podejście do implementacji

Faza 1 buduje fundament (build config + tema + czcionki), faza 2 dodaje komponenty i aktywuje temat w root App. Przerwa między fazami pozwala zweryfikować, że moduł shared kompiluje się z nową temą zanim cokolwiek zostanie użyte w UI.

Przed startem fazy 1 — pobrać czcionki ręcznie (patrz „Wymagania wstępne").

---

## Wymagania wstępne — czcionki (krok ręczny)

Przed implementacją fazy 1 pobierz z Google Fonts i umieść w `frontend/shared/src/commonMain/composeResources/font/`:

| Plik | Źródło |
|------|--------|
| `bricolage_grotesque_regular.ttf` | Google Fonts → Bricolage Grotesque → Regular 400 |
| `bricolage_grotesque_semibold.ttf` | Google Fonts → Bricolage Grotesque → SemiBold 600 |
| `bricolage_grotesque_bold.ttf` | Google Fonts → Bricolage Grotesque → Bold 700 |
| `jetbrains_mono_regular.ttf` | Google Fonts → JetBrains Mono → Regular 400 |
| `jetbrains_mono_bold.ttf` | Google Fonts → JetBrains Mono → Bold 700 |

Nazewnictwo: małe litery, podkreślenia. Dokładnie jak w tabeli.

---

## Faza 1: Build config + System motywu

### Przegląd

Dodanie `material-icons-extended` do zależności i wdrożenie trzech plików systemu motywu (Color, Theme, Type) z czcionkami.

### Wymagane zmiany

#### 1. Konfiguracja build

**Plik**: `frontend/shared/build.gradle.kts`

**Cel**: Dodanie biblioteki ikon rozszerzonych, których używają komponenty z prototypu.

**Kontrakt**: W bloku `commonMain.dependencies { ... }` dopisz jedną linię:
```kotlin
implementation(compose.materialIconsExtended)
```

#### 2. Paleta kolorów

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Color.kt`

**Cel**: Surowe tokeny kolorystyczne palety „Dawn Run" oraz helper `accentColorForId()` dla deterministycznych kolorów kolekcji.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.theme`. Skopiuj z `.tmp/UI/src/theme/Color.kt` z adaptacją pakietu. Dopisz na końcu:

```kotlin
private val accentPalette = listOf(Ember, Peach, Color(0xFF7B68EE), Color(0xFF20B2AA), Color(0xFFFF8C00), Color(0xFF9ACD32))

fun accentColorForId(id: String): Color =
    accentPalette[id.hashCode().let { if (it < 0) -it else it } % accentPalette.size]
```

#### 3. Semantyczne tokeny motywu

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Theme.kt`

**Cel**: `FiszkiColors`, `FiszkiThemedScreen` i `FiszkiAppTheme` — warstwa semantyczna na surowej palecie.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.theme`. Skopiuj z `.tmp/UI/src/theme/Theme.kt` z adaptacją pakietu.

#### 4. Typografia

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/theme/Type.kt`

**Cel**: Definicje `FontFamily` dla Bricolage Grotesque i JetBrains Mono z zasobów projektu oraz style tekstowe `bricolage()`, `mono()`, `capsMono()`, `bigNumber()`.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.theme`. Skopiuj z `.tmp/UI/src/theme/Type.kt` z adaptacją pakietu. Ładowanie czcionek przez `Res.font.*` — wymaga `@Composable` (font loading API w CMP jest composable).

#### 5. Zasoby czcionek

**Plik**: `frontend/shared/src/commonMain/composeResources/font/` (katalog)

**Cel**: Pliki .ttf dostępne jako `Res.font.*` w runtime.

**Kontrakt**: Utwórz katalog `font/` w `composeResources/` i wrzuć 5 plików pobranych w wymaganiach wstępnych. Gradle automatycznie generuje `Res.font.*` z nazw plików.

### Kryteria sukcesu fazy 1

#### Weryfikacja automatyczna

- `./gradlew :shared:compileDebugKotlinAndroid` bez błędów kompilacji

#### Weryfikacja ręczna

- Katalog `theme/` zawiera 3 pliki: `Color.kt`, `Theme.kt`, `Type.kt`
- Katalog `composeResources/font/` zawiera 5 plików `.ttf`
- Brak nierozwiązanych importów w plikach motywu

---

## Faza 2: Komponenty UI + Aktywacja motywu

### Przegląd

Wdrożenie komponentów wielokrotnego użytku (Flag, TrackBar, MediaControls) i opakowanie `App.kt` przez `FiszkiAppTheme`. Po tej fazie pełna weryfikacja na Androidzie i WasmJS.

### Wymagane zmiany

#### 1. Flagi państw

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/Flag.kt`

**Cel**: Rysowany na Canvas komponent `Flag(code: String, size: Dp)` z flagami dla kodów `pl`, `en`, `de`, `es`, `fr`, `it`, `gb` oraz mapa `LanguageNames`.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.ui.components`. Skopiuj z `.tmp/UI/src/ui/components/Flag.kt` z adaptacją pakietu. Nie wymaga `LocalFiszkiColors` — rysuje surowe SVG-style kształty na Canvas.

#### 2. Komponenty ogólne

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/Components.kt`

**Cel**: `StudyChip(label)`, `TrackBar(progress, accent)`, `CapsLabel(text, color)` używające `LocalFiszkiColors.current` z motywu.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.ui.components`. Skopiuj z `.tmp/UI/src/ui/components/Components.kt` z adaptacją pakietu i importem `pl.rkarpinski.fiszkiwbiegu.theme.*`.

#### 3. Kontrolki mediów

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/ui/components/MediaControls.kt`

**Cel**: `CtrlButton(icon, primary)` i `MediaControls(isPlaying, onPrev, onPlayPause, onNext)` używające `LocalFiszkiColors`.

**Kontrakt**: Nowy plik, pakiet `pl.rkarpinski.fiszkiwbiegu.ui.components`. Skopiuj z `.tmp/UI/src/ui/components/MediaControls.kt` z adaptacją pakietu i importem `pl.rkarpinski.fiszkiwbiegu.theme.*`.

#### 4. Aktywacja motywu w App.kt

**Plik**: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

**Cel**: Udostępnienie `LocalFiszkiColors` i `LocalFiszkiThemeOverride` całemu drzewu komponentów — istniejące ekrany nie czytają tych CompositionLocal, więc nie zmienia się ich wygląd w tej fazie.

**Kontrakt**: Znajdź root `@Composable` w `App.kt` (funkcja `App()` lub `FiszkiWBieguApp()`). Opakuj jej zawartość przez `FiszkiAppTheme(override = null) { ... }`. Import: `pl.rkarpinski.fiszkiwbiegu.theme.FiszkiAppTheme`.

### Kryteria sukcesu fazy 2

#### Weryfikacja automatyczna

- `./gradlew :androidApp:assembleDebug` kończy się sukcesem (exit code 0)
- `./gradlew :webApp:wasmJsBrowserDevelopmentRun` kompiluje się bez błędów

#### Weryfikacja ręczna

- APK instaluje się na urządzeniu/emulatorze
- Aplikacja uruchamia się bez crashu
- Ekrany wyglądają jak przed zmianą (istniejące ekrany nie używają jeszcze FiszkiThemedScreen — nie powinno być regresji wizualnej, oprócz ewentualnych zmian koloru wynikających z MaterialTheme)
- Brak błędów w Logcat przy starcie

---

## Strategia testowania

### Testy automatyczne

- Kompilacja modułu `shared` na Androidzie (faza 1)
- Kompilacja pełnej aplikacji na Androidzie i WasmJS (faza 2)
- `./gradlew :shared:test` — upewnij się, że istniejące testy nadal przechodzą

### Testy ręczne

1. Zainstaluj APK i uruchom aplikację — sprawdź, że wszystkie ekrany działają jak przed zmianą
2. Przejdź przez pełny flow: logowanie → kolekcje → fiszki → sesja nauki
3. Sprawdź konsolę WasmJS pod kątem błędów Canvas/Font w przeglądarce

---

## Referencje

- Prototyp UI: `.tmp/UI/src/theme/`, `.tmp/UI/src/ui/components/`
- Instrukcja handoff: `.tmp/UI/README.md`
- Roadmap S-04-A: `context/foundation/roadmap.md`
- Następna faza: `context/changes/ui-reskin-screens/` (S-04-B, tworzona po ukończeniu tej fazy)

---

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Build config + System motywu

#### Automatyczne

- [x] 1.1 `./gradlew :shared:compileAndroidMain` — bez błędów kompilacji — 39b94a3

#### Ręczne

- [x] 1.2 Katalog `theme/` zawiera `Color.kt`, `Theme.kt`, `Type.kt` — 39b94a3
- [x] 1.3 Katalog `composeResources/font/` zawiera 5 plików `.ttf` — 39b94a3
- [x] 1.4 Brak nierozwiązanych importów w plikach motywu — 39b94a3

### Faza 2: Komponenty UI + Aktywacja motywu

#### Automatyczne

- [x] 2.1 `./gradlew :androidApp:assembleDebug` — exit code 0
- [x] 2.2 `./gradlew :webApp:wasmJsBrowserDevelopmentRun` — kompiluje się bez błędów

#### Ręczne

- [x] 2.3 APK instaluje się i uruchamia bez crashu
- [x] 2.4 Pełny flow aplikacji (login → kolekcje → fiszki → nauka) działa bez regresji
- [x] 2.5 Brak błędów Canvas/Font w Logcat i konsoli WasmJS
