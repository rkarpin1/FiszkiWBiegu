# Material Design 3 Compatibility — Krótki plan

> Pełny plan: `context/changes/material-design-3/plan.md`

## Co i dlaczego

`FiszkiThemedScreen` tworzy `ColorScheme` z zaledwie 6 z 27 tokenów MD3, a ekrany i komponenty używają własnego `LocalFiszkiColors` zamiast `MaterialTheme.colorScheme`. Celem jest pełna zgodność z MD3 API przy zachowaniu wyglądu Dawn Run — tak żeby ekrany korzystały ze standardowego `MaterialTheme.colorScheme` i `MaterialTheme.shapes`.

## Punkt wyjścia

`theme/Theme.kt` ma `FiszkiColors` (12 własnych tokenów) i niepełny `ColorScheme`. Wszystkie 7 ekranów i komponenty w `ui/components/` pobierają kolory przez `LocalFiszkiColors.current`. Kształty są hardkodowane (`RoundedCornerShape(12.dp)` itp.) — brak `MaterialTheme.shapes`.

## Pożądany stan końcowy

Ekrany i komponenty używają `MaterialTheme.colorScheme.surface`, `.onSurface`, `.onSurfaceVariant`, `.outlineVariant`, `.primary`, `.onPrimary` itd. bezpośrednio. `LocalFiszkiColors` pozostaje tylko dla tokenów bez odpowiednika MD3 (`mute2`, `surface3`, `accentSoft`, `textInv`). `MaterialTheme.shapes` zdefiniowany. Wygląd aplikacji bez zmian.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---------|-------|---------------------|--------|
| Dawn Run vs MD3 palette | Zachowaj Dawn Run, zmapuj na ColorScheme | Wygląd aplikacji nie zmienia się | Plan |
| LocalFiszkiColors | Zostaje jako rozszerzenie | Ma tokeny bez odpowiednika MD3 (mute2, surface3) | Plan |
| Wersja material3 | Zostaje alpha (1.11.0-alpha07) | Brak potrzeby upgrade w tym zakresie | Plan |
| Upgrade wersji | Poza zakresem | Osobna zmiana, inne ryzyko | Plan |
| Elevation system | Poza zakresem | Nie zostało wybrane jako must-have | Plan |
| Weryfikacja | Ręczny przegląd APK | Jedyna pewna metoda dla zmian wizualnych | Plan |

## Zakres

**W zakresie:**
- Rozszerzenie `ColorScheme` o brakujące tokeny MD3 (secondary, tertiary, error, surfaceVariant, outlineVariant, ...)
- Migracja `ui/components/` (Components.kt, MediaControls.kt, LangSelect.kt)
- Migracja 7 ekranów z `LocalFiszkiColors` na `MaterialTheme.colorScheme`
- Dodanie `MaterialTheme.shapes` z mapowaniem na obecne hardkodowane wartości

**Poza zakresem:**
- Upgrade `material3` z `1.11.0-alpha07`
- Zmiana palety kolorów Dawn Run
- Elevation/shadow system
- Usunięcie `LocalFiszkiColors` (zostaje dla rozszerzeń)

## Architektura / Podejście

Trzy sekwencyjne fazy: fundament (Theme.kt) → komponenty → ekrany. W każdym pliku: `val scheme = MaterialTheme.colorScheme` obok `val c = LocalFiszkiColors.current`; zamiana `c.surface` → `scheme.background`, `c.mute` → `scheme.onSurfaceVariant` itd. Tokeny bez MD3 odpowiednika (`mute2`, `surface3`, `accentSoft`) pozostają przez `LocalFiszkiColors`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. Fundamenty motywu | Pełny ColorScheme + MaterialTheme.shapes | Błędne mapowanie tokenów → błędy kompilacji |
| 2. Komponenty | ui/components/ zmigrowani | Regresja wizualna w chip/media/lang komponentach |
| 3. Ekrany | Wszystkie 7 ekranów zmigrowani | Regresja wizualna; dużo plików naraz |

**Wymagania wstępne:** Brak — zmiana jest samodzielna.
**Szacowany nakład pracy:** ~2 sesje w 3 fazach.

## Otwarte ryzyka i założenia

- `material3 = "1.11.0-alpha07"` może mieć nieudokumentowane różnice w API kształtów — do weryfikacji w fazie 1.
- `LocalFiszkiColors` musi pozostać zsynchronizowany z `ColorScheme` — każda zmiana koloru wymaga aktualizacji obu.

## Kryteria sukcesu (podsumowanie)

- Aplikacja kompiluje się bez błędów po każdej fazie
- Wszystkie 7 ekranów wygląda identycznie na urządzeniu (ręczny przegląd APK)
- Dark mode działa bez regresji
