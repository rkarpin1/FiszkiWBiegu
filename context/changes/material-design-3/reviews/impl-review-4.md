<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Material Design 3 Compatibility (post-fix 3)

- **Plan**: context/changes/material-design-3/plan.md
- **Scope**: All 3 phases (4th review — post impl-review-3 fixes + commit 20e44df)
- **Date**: 2026-05-30
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical  2 warnings  1 observation

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

### F1 — onBackground na powierzchniach scheme.surface (3 pominięte lokalizacje)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Plan Adherence
- **Location**: CollectionFormScreen.kt:286 / LoginScreen.kt:136 / ProfileScreen.kt:92
- **Detail**: impl-review-3 F1 naprawiła 8 z 11 miejsc gdzie onBackground był użyty na surface-coloured kontenerze. Trzy lokalizacje zostały pominięte: (1) CollectionFormScreen.kt:286 — nagłówek w ModalBottomSheet, który renderuje na własnej powierzchni surface; (2) LoginScreen.kt:136 — tekst enabled AuthButton na tle scheme.surface; (3) ProfileScreen.kt:92 — displayName w karcie profilu na background(scheme.surface). Wizualnie niewidoczne w bieżącej palecie, ale semantycznie błędne.
- **Fix**: Zamień scheme.onBackground → scheme.onSurface we wszystkich 3 miejscach. W LoginScreen.kt:136 zmień tylko enabled-case; c.mute2 dla disabled pozostaje.
  - Strength: Kontynuuje zasadę F1 z impl-review-3; spójna z 8 już naprawionymi lokalizacjami.
  - Tradeoff: Brak widocznego efektu wizualnego w bieżącej palecie.
  - Confidence: HIGH — identyczna zasada, identyczna paleta.
  - Blind spot: Mogą istnieć inne lokalizacje poza 3 znalezionymi.
- **Decision**: FIXED (onBackground → onSurface w CollectionFormScreen.kt:286, LoginScreen.kt:136, ProfileScreen.kt:91)

### F2 — TextButton "Anuluj": color=onSurface dodany niespójnie w commit 20e44df

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Dimension**: Pattern Consistency
- **Location**: CollectionsScreen.kt:425 / FlashcardsScreen.kt:138,488 / (brak w CardFormScreen.kt:130)
- **Detail**: Commit 20e44df (opisany jako "consistent formatting") dodał `color = MaterialTheme.colorScheme.onSurface` do trzech przycisków "Anuluj" w dialogach usuwania. To zmiana UI: MD3 TextButton domyślnie używa colorScheme.primary (Ember/Peach) jako koloru tekstu; po zmianie "Anuluj" jest koloru onSurface (muted, szary), traci interaktywny affordance. Dodatkowo CardFormScreen.kt:130 ma identyczny TextButton("Anuluj") i NIE otrzymał tego override — wzorzec niespójny.
- **Fix A ⭐ Recommended**: Usuń color=onSurface ze wszystkich 3 miejsc, zostaw domyślny primary MD3.
  - Strength: Przywraca MD3 default; spójna z CardFormScreen bez dotknięcia.
  - Tradeoff: "Anuluj" będzie w kolorze Ember/Peach — może wyglądać intensywnie obok error.
  - Confidence: HIGH — MD3 spec: TextButton.contentColor = primary.
  - Blind spot: Nie weryfikowaliśmy czy obecny wygląd był celową decyzją projektową.
- **Fix B**: Zostaw onSurface ale dodaj też do CardFormScreen.kt:130.
  - Strength: Przynajmniej spójny wzorzec we wszystkich dialogach.
  - Tradeoff: Odchodzi od MD3 default; neutralizuje affordance dismiss.
  - Confidence: MEDIUM — konsekwentne odejście od MD3 możliwe jeśli celowe.
  - Blind spot: Brak.
- **Decision**: FIXED via Fix B (zachowano color=onSurface w CollectionsScreen.kt:425, FlashcardsScreen.kt:138,488; dodano do CardFormScreen.kt:130 dla spójności)

### F3 — Nieidomatyczne umieszczenie lambda w FlashcardsScreen.kt

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Pattern Consistency
- **Location**: FlashcardsScreen.kt:136–139
- **Detail**: TextButton dismiss button z klamrą na nowej linii po `)` — non-idiomatic Kotlin, niespójne z każdym innym TextButton w codebase.
- **Fix**: Przenieś `{` do tej samej linii co `TextButton(...)`.
- **Decision**: FIXED (naprawiono przy okazji F2 — lambda brace przeniesiona do tej samej linii co TextButton)
