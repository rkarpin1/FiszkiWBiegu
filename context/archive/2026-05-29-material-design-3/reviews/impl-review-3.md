<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Material Design 3 Compatibility (post-fix 2)

- **Plan**: context/changes/material-design-3/plan.md
- **Scope**: All 3 phases (3rd review — post impl-review-2 fixes)
- **Date**: 2026-05-30
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical  3 warnings  1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — scheme.onBackground na elementach z tłem scheme.surface (8 lokalizacji)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Plan Adherence + Pattern Consistency
- **Location**: MediaControls.kt:48 / CollectionFormScreen.kt:133 / FlashcardsScreen.kt:178 / CardFormScreen.kt:159 / LearningScreen.kt:120 / CollectionsScreen.kt:139,333 / FlashcardsScreen.kt:399
- **Detail**: Poprawka F3 (onBackground → onSurface na surface-coloured kontenerach) została wdrożona w Components.kt i LangSelect.kt, ale ominęła wzorzec "przycisku wstecz" (Box z background(scheme.surface) + Icon z tint) we wszystkich 4 ekranach formularzy/nauki oraz secondary CtrlButton. Dodatkowo trzy elementy tekstowe siedzą na scheme.surface tle, ale przekazują scheme.onBackground. Wizualnie niewidoczne (onBackground == onSurface w tej palecie), ale semantycznie błędne.
- **Fix**: Zamień scheme.onBackground → scheme.onSurface we wszystkich 8 miejscach.
  - Strength: Spójne z poprawioną już logiką w Components.kt:43 i LangSelect.kt:57/87.
  - Tradeoff: Brak widocznego efektu w bieżącej palecie.
  - Confidence: HIGH — identyczna zasada już zastosowana w innych plikach.
  - Blind spot: Mogą istnieć dodatkowe lokalizacje poza 8 znalezionymi.
- **Decision**: FIXED (onBackground → onSurface we wszystkich 8 miejscach)

### F2 — Color.White hardcoded w ProfileScreen i MediaControls

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: ProfileScreen.kt:83–86, ProfileScreen.kt:122, MediaControls.kt:48
- **Detail**: Trzy miejsca używają hardkodowanego Color.White jako koloru tekstu/ikony na tle scheme.primary. Poprawny token MD3 to scheme.onPrimary (= Color.White w obu schematach tej palety). Przy zmianie primary na ciemny odcień onPrimary odwróci się, a Color.White nie.
- **Fix**: Zamień Color.White → scheme.onPrimary we wszystkich 3 miejscach.
- **Decision**: FIXED (Color.White → scheme.onPrimary we wszystkich 3 miejscach; usunięto martwe importy Color)

### F3 — Delete sheet: c.accentSoft (ciepła brzoskwinia) z scheme.error (czerwień)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Pattern Consistency
- **Location**: CollectionFormScreen.kt:277
- **Detail**: Ikona Delete jest tintowana scheme.error (czerwień), ale kontener-ikony ma tło c.accentSoft (Peach/Ember2). Ciepła brzoskwinia + czerwona ikona to niespójny ton. MD3 definiuje scheme.errorContainer / scheme.onErrorContainer dokładnie do tego wzorca.
- **Fix A ⭐ Recommended**: Ustaw errorContainer w Theme.kt i użyj scheme.errorContainer + scheme.onErrorContainer.
  - Strength: Pełna spójność semantyczna MD3. Unifikuje z dialogami usuwania.
  - Tradeoff: Wymaga dodania 2 tokenów do Theme.kt; wygląd zmieni się.
  - Confidence: MEDIUM — projektant musi zatwierdzić kolor errorContainer dla Dawn Run.
  - Blind spot: Nie weryfikowaliśmy aktualnego wyglądu na urządzeniu.
- **Fix B**: Zmień tint ikony na scheme.primary, usuń c.accentSoft.
  - Strength: Spójna warm-orange destrukcja bez konfliktu tonalnego.
  - Tradeoff: Niespójne z dialogami usuwania (scheme.error).
  - Confidence: LOW — wbrew wcześniejszej poprawce F1.
  - Blind spot: Brak.
- **Decision**: FIXED via Fix A (dodano errorContainer/onErrorContainer do Theme.kt; ikona używa scheme.errorContainer + scheme.onErrorContainer)

### F4 — Martwe importy RoundedCornerShape w 2 plikach

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Plan Adherence
- **Location**: LangSelect.kt:14, CardFormScreen.kt:19
- **Detail**: Migracja kształtów usunęła wszystkie wywołania RoundedCornerShape, ale importy pozostały — leftover po migracji.
- **Fix**: Usuń oba importy.
- **Decision**: FIXED (usunięto importy z LangSelect.kt i CardFormScreen.kt)
