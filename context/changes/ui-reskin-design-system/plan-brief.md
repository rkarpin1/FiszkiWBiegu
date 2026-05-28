# UI Reskin — Faza A: Design System — Krótki plan

> Pełny plan: `context/changes/ui-reskin-design-system/plan.md`

## Co i dlaczego

Wdrożenie systemu motywu i komponentów bazowych z prototypu `.tmp/UI` do modułu `shared` — bez dotykania logiki biznesowej, ViewModeli ani istniejących ekranów. Celem jest stworzenie fundamentu wizualnego (paleta, czcionki, komponenty), na którym faza B zbuduje przepisane ekrany.

## Punkt wyjścia

Moduł `shared` ma działające 4 ekrany (Login, Collections, Flashcards, Learning) z domyślnym Material3. Brak katalogu `theme/` i `ui/components/`. `compose.components.resources` jest już skonfigurowany; `material-icons-extended` trzeba dodać.

## Pożądany stan końcowy

Katalogi `theme/` i `ui/components/` istnieją w `commonMain`, czcionki są w `composeResources/font/`, `App.kt` jest opakowany przez `FiszkiAppTheme(override = null)`. Aplikacja kompiluje się i uruchamia bez regresji — istniejące ekrany wyglądają jak wcześniej (jeszcze nie używają `FiszkiThemedScreen`).

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego |
|---------|-------|----------|
| Czcionki | Ręczne pobranie przed implementacją | Najprostsze; brak nowych zależności toolingowych |
| Material Icons | `compose.materialIconsExtended` w commonMain | Pełny zestaw ikon bez ręcznego zarządzania wersjami |
| Kompilacja | Android + WasmJS | Flag.kt używa Canvas — warto zweryfikować WasmJS w tej fazie |
| App.kt wrapping | `FiszkiAppTheme(override = null)` w fazie A | Bezpieczne — to tylko `CompositionLocalProvider`, nie zmienia wyglądu ekranów |
| expect/actual z prototypu | Pomijamy (TtsEngine, SocialAuthProvider, TranslateService) | Projekt ma działający TTS i auth |
| accentColor helper | Wolnostojąca funkcja w `theme/Color.kt` | Brak zanieczyszczenia DTO importem `Color` |

## Zakres

**W zakresie:**
- `theme/Color.kt`, `theme/Theme.kt`, `theme/Type.kt`
- `ui/components/Flag.kt`, `ui/components/Components.kt`, `ui/components/MediaControls.kt`
- `composeResources/font/` — 5 plików .ttf
- `shared/build.gradle.kts` — dodanie `materialIconsExtended`
- `App.kt` — wrapping przez `FiszkiAppTheme`

**Poza zakresem:**
- Zmiany w istniejących ekranach (faza B)
- Nowe ekrany: Profile, CollectionDetail, CollectionForm, CardForm (faza C)
- Backend: /me, lastStudied, progress (faza D)
- TtsEngine, SocialAuthProvider, TranslateService z prototypu

## Architektura / Podejście

Dwa nowe pakiety w `commonMain`: `theme/` (Color, Theme, Type) i `ui/components/` (Flag, Components, MediaControls). Pliki skopiowane z `.tmp/UI/src/` z adaptacją package names (`pl.fiszki.wbiegu` → `pl.rkarpinski.fiszkiwbiegu`). `FiszkiAppTheme` to czysty `CompositionLocalProvider` — nie duplikuje `MaterialTheme`, który zostaje w istniejących ekranach do czasu fazy B.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. Build config + Motyw | `theme/`, czcionki, materialIconsExtended | Type.kt używa `Res.font.*` — wymaga .ttf przed kompilacją |
| 2. Komponenty + Aktywacja | `ui/components/`, App.kt z FiszkiAppTheme | Canvas w Flag.kt może mieć edge cases na WasmJS |

**Wymagania wstępne:** Pliki .ttf pobrane z Google Fonts przed fazą 1. Brak innych zewnętrznych zależności.  
**Szacowany nakład:** ~1 sesja implementacyjna, 2 fazy

## Otwarte ryzyka i założenia

- `compose.materialIconsExtended` dostępny przez type-safe accessor w CMP 1.11.0 — do potwierdzenia przy pierwszej kompilacji
- Istniejące ekrany nie czytają `LocalFiszkiColors` → brak regresji wizualnej (założenie do weryfikacji ręcznej po fazie 2)

## Kryteria sukcesu

- `./gradlew :androidApp:assembleDebug` i `wasmJsBrowserDevelopmentRun` kończą się sukcesem
- APK instaluje się i uruchamia bez crashu; pełny flow (login → kolekcje → nauka) działa
