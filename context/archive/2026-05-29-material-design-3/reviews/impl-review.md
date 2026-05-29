<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Material Design 3 Compatibility

- **Plan**: context/changes/material-design-3/plan.md
- **Scope**: All 3 phases
- **Date**: 2026-05-29
- **Verdict**: NEEDS ATTENTION (all findings fixed during triage)
- **Findings**: 0 critical  3 warnings  3 observations

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

### F1 — Error message rendered in scheme.primary instead of scheme.error

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Safety & Quality
- **Location**: screens/login/LoginScreen.kt:97
- **Detail**: Tekst błędu logowania używał `color = scheme.primary` (Ember) zamiast `color = scheme.error`. Semantycznie błędne — scheme.error jest dedykowanym tokenem dla błędów.
- **Fix**: Zamień `color = scheme.primary` na `color = scheme.error` w linii 97.
- **Decision**: FIXED

### F2 — Modifier order: .border() po .clickable() w LastUsedHero

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: CollectionsScreen.kt:292–295
- **Detail**: Kolejność `.clip → .background → .clickable → .border → .padding` — border po clickable oznacza, że ramka rysuje się na warstwie ripple. Commit 4eb20db naprawił clickable/padding ale nie dokończył poprawki. LangSelect.kt robi to poprawnie: `.clip → .background → .border → .clickable → .padding`.
- **Fix**: Przenieś `.border(...)` przed `.clickable(...)` w LastUsedHero.
- **Decision**: FIXED

### F3 — RoundedCornerShape(19.dp) nie zastąpiony w CardFormScreen

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Plan Adherence
- **Location**: CardFormScreen.kt:260, 262
- **Detail**: Plan jawnie mapuje "18–19dp = large", ale `RoundedCornerShape(19.dp)` na przycisku "Przetłumacz" pozostał jako hardkodowana wartość (2 wystąpienia). Jedyna wartość z planu, która nie została zastąpiona.
- **Fix**: Zastąp oba `RoundedCornerShape(19.dp)` przez `MaterialTheme.shapes.large`.
- **Decision**: FIXED

### F4 — Niezamapowane kształty (14dp, 16dp, 24dp, 30dp) w 6 plikach

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Plan Adherence
- **Location**: CollectionsScreen.kt (24dp ×4), CollectionFormScreen.kt (14dp ×1), FlashcardsScreen.kt (30dp ×1, 16dp ×2), LearningScreen.kt (16dp ×4), LoginScreen.kt (16dp ×2), ProfileScreen.kt (24dp ×2)
- **Detail**: Plan definiował mapowanie tylko dla 4/8/12/18-19/28dp. Wartości 14dp, 16dp, 24dp, 30dp nie miały odpowiednika w systemie tokenów. Mapowanie zastosowane: 14dp → `shapes.large`, 16dp → `shapes.large`, 24dp → `shapes.extraLarge`, 30dp → `CircleShape`. Usunięto 6 zbędnych importów `RoundedCornerShape`.
- **Fix A ⭐ Applied**: Zmapowano wszystkie wartości na najbliższy istniejący tier i usunięto zbędne importy.
- **Decision**: FIXED via Fix A

### F5 — c.mute2 dla separatora w LaneRow vs scheme.onSurfaceVariant sąsiadów

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: CollectionsScreen.kt:259
- **Detail**: `Text("·", color = c.mute2)` w LaneRow, gdzie sąsiedni tekst "N dni temu" używa `scheme.onSurfaceVariant`. c.mute2 (~32% alpha) jest wyraźnie ciemniejszy od onSurfaceVariant (~55% alpha) — separator wizualnie za ciemny względem kontekstu. Usunięto też `val c = LocalFiszkiColors.current` z LaneRow i import `LocalFiszkiColors` z pliku (jedyne użycie).
- **Fix**: Zastąpiono `c.mute2` przez `scheme.onSurfaceVariant`; usunięto `val c` i import.
- **Decision**: FIXED

### F6 — RoundedCornerShape(30.dp) na FAB zamiast CircleShape

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: FlashcardsScreen.kt:146
- **Detail**: FAB używał `RoundedCornerShape(30.dp)` zamiast `CircleShape`. MediaControls.kt używa `CircleShape` poprawnie — dwa różne sposoby na kółkowy kształt w tym samym projekcie.
- **Fix**: Zastąpiono `RoundedCornerShape(30.dp)` przez `CircleShape`; dodano import `CircleShape`.
- **Decision**: FIXED (jako część F4)
