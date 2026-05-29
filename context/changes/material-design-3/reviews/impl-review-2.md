<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Material Design 3 Compatibility (post-fix)

- **Plan**: context/changes/material-design-3/plan.md
- **Scope**: All 3 phases (post-fix review after F1–F6 applied)
- **Date**: 2026-05-29
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical  3 warnings  6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Delete affordances tinted scheme.primary instead of scheme.error

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Safety & Quality
- **Location**: CollectionFormScreen.kt:157, 280, 311 / CardFormScreen.kt:184
- **Detail**: Ikony Delete i przycisk „Usuń kolekcję" używają scheme.primary (Ember/orange). Destrukcyjne akcje powinny używać scheme.error (czerwień). Dialogi w FlashcardsScreen.kt:127 i CardFormScreen.kt:127 już poprawnie używają scheme.error.
- **Fix A ⭐ Recommended**: Zmień tint/containerColor na scheme.error we wszystkich 4 miejscach.
  - Strength: Spójne z dialogami w CardFormScreen i FlashcardsScreen, które już używają scheme.error.
  - Tradeoff: Delete button zmieni kolor z orange na czerwień.
  - Confidence: HIGH — FlashcardsScreen już tak robi; to ta sama akcja.
  - Blind spot: Brak.
- **Fix B**: Zostaw scheme.primary, ujednolicić dialogi do scheme.primary.
  - Strength: Spójna paleta warm-orange na wszystkich destrukcyjnych akcjach.
  - Tradeoff: Odbiega od konwencji MD3 gdzie error = czerwień.
  - Confidence: LOW — wbrew semantyce MD3 tokenów.
  - Blind spot: Brak.
- **Decision**: FIXED via Fix A

### F2 — StudyChip: RoundedCornerShape(10.dp) pominięty w migracji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Plan Adherence
- **Location**: ui/components/Components.kt:34, 36
- **Detail**: Plan: "Hardkodowane RoundedCornerShape(Ndp) → MaterialTheme.shapes.*". Dwa wystąpienia RoundedCornerShape(10.dp) w StudyChip (clip + border) pozostały niezmienione.
- **Fix**: Zastąp oba RoundedCornerShape(10.dp) przez MaterialTheme.shapes.small (8dp). Aktualizuj zarówno .clip jak i .border.
- **Decision**: FIXED

### F3 — scheme.onBackground na scheme.surface kontenerach (powinno być onSurface)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Pattern Consistency
- **Location**: Components.kt:44, LangSelect.kt:57/87, CollectionFormScreen.kt:139, LearningScreen.kt:205/210/216
- **Detail**: W MD3 tekst na surface-coloured kontenerach powinien używać scheme.onSurface. Brak widocznego efektu w bieżącej palecie (onBackground == onSurface), ale semantycznie niepoprawne.
- **Fix A ⭐ Recommended**: Zostaw tak jak jest — brak widocznego efektu w tej palecie.
  - Strength: Zero ryzyka regresji. Skala problemu nie uzasadnia zmiany bez widocznego efektu.
  - Tradeoff: Dług semantyczny — ryzyko przyszłych regresji przy zmianie palety.
  - Confidence: HIGH — onBackground == onSurface == Cream/Ink w obu schematach.
  - Blind spot: Przyszłe zmiany palety.
- **Fix B**: Zamień scheme.onBackground → scheme.onSurface we wszystkich 6 miejscach.
  - Strength: Poprawna semantyka MD3. Odporna na zmiany palety.
  - Tradeoff: Szeroka zmiana bez widocznego efektu teraz.
  - Confidence: MEDIUM.
  - Blind spot: Mogą być inne miejsca poza przeszukiwanymi plikami.
- **Decision**: FIXED via Fix B

### F4 — App.kt:89 używa c.surface — poza zakresem planu, niezmigrowane

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Plan Adherence
- **Location**: App.kt:89
- **Detail**: Scaffold containerColor = c.surface — App.kt nie był w planie. Jedyne pozostałe miejsce poza migracją.
- **Fix**: Zastąp c.surface przez MaterialTheme.colorScheme.background.
- **Decision**: FIXED (App.kt całkowicie odmigrowny — Scaffold + NavigationBar)

### F5 — TrackBar: RoundedCornerShape(3.dp) pominięty

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Plan Adherence
- **Location**: ui/components/Components.kt:73
- **Detail**: TrackBar segment używa RoundedCornerShape(3.dp). Najbliższy token to MaterialTheme.shapes.extraSmall (4dp).
- **Fix**: Zastąp RoundedCornerShape(3.dp) przez MaterialTheme.shapes.extraSmall.
- **Decision**: FIXED (import RoundedCornerShape usunięty z Components.kt)

### F6 — c.accentSoft == scheme.secondary — 3 ekrany mogą usunąć LocalFiszkiColors

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Pattern Consistency
- **Location**: LearningScreen.kt:183, LoginScreen.kt:75, ProfileScreen.kt:78
- **Detail**: DarkColors.accentSoft = Peach == darkColorScheme.secondary. LightColors.accentSoft = Ember2 == lightColorScheme.secondary. Te 3 ekrany mogłyby zastąpić c.accentSoft → scheme.secondary i całkowicie usunąć LocalFiszkiColors z LoginScreen i ProfileScreen.
- **Fix**: Zastąp c.accentSoft → scheme.secondary w 3 plikach. Usuń val c i import LocalFiszkiColors w LoginScreen.kt i ProfileScreen.kt.
- **Decision**: FIXED (LocalFiszkiColors usunięte z ProfileScreen i zewnętrznego scope LoginScreen)

### F7 — FAB używa onBackground/background zamiast inverseSurface/inverseOnSurface

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: CollectionsScreen.kt:98–105, FlashcardsScreen.kt:144–151
- **Detail**: FAB używa .background(scheme.onBackground) + tint=scheme.background. MD3 dedykuje inverseSurface/inverseOnSurface dokładnie do tego wzorca. Wizualnie identyczne w tej palecie.
- **Fix**: Zamień scheme.onBackground → scheme.inverseSurface i scheme.background → scheme.inverseOnSurface w obu FAB.
- **Decision**: FIXED

### F8 — FiszkiColors: pola surface/text/mute/line/accent/onAccent nieużywane po migracji

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Architecture
- **Location**: theme/Theme.kt:19–62
- **Detail**: Po migracji pola surface, surface2, text, mute, line, accent, onAccent w FiszkiColors nie są używane w żadnym przejrzanym pliku. Martwy kod.
- **Fix**: Usuń nieużywane pola z FiszkiColors i DarkColors/LightColors po potwierdzeniu, że webApp/iosApp ich nie używa.
- **Decision**: FIXED (FiszkiColors zredukowane do 2 pól: mute2, accentSoft)
