<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Wybór języka kolekcji

- **Plan**: context/changes/collection-language-select/plan.md
- **Scope**: All phases (1–3 of 3)
- **Date**: 2026-05-29
- **Verdict**: ZAAKCEPTOWANO
- **Findings**: 1 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — confirmEdit hardkoduje "pl","en" — cicha nadpisanie języków

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zatrzymaj się, żeby to przemyśleć
- **Dimension**: Safety & Quality
- **Location**: CollectionsViewModel.kt:66
- **Detail**: confirmEdit() wywołuje repo.rename(id, newName, "pl", "en") z hardkodowanymi językami. Metoda jest nieosiągalna z UI (CollectionFormScreen używa updateCollection), ale requestEdit/confirmEdit są publiczne. Każde wywołanie confirmEdit() cicho nadpisze języki kolekcji na pl/en.
- **Fix A ⭐ Recommended**: Usuń confirmEdit, requestEdit, cancelEdit i pola editingCollectionId/editingCollectionName z UiState.
  - Strength: Eliminuje klasę błędu; czyści ~20 linii martwego kodu.
  - Tradeoff: Nieodwracalne jeśli stary przepływ inline-edit miałby wrócić.
  - Confidence: HIGH — requestEdit nie ma callsites poza VM.
  - Blind spot: Żaden istotny.
- **Fix B**: Dodaj parametry języka do confirmEdit.
  - Strength: Zachowuje metodę na wypadek przyszłego użycia.
  - Tradeoff: Nadal martwy kod z lepszą sygnaturą.
  - Confidence: MED
  - Blind spot: Kto i kiedy dostarczyłby języki?
- **Decision**: FIXED via Fix A — usunięto confirmEdit/requestEdit/cancelEdit i pola editingCollectionId/editingCollectionName

### F2 — Backend nie waliduje pustej nazwy kolekcji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Safety & Quality
- **Location**: collections.rs:37, :67
- **Detail**: CollectionRequest.name nie jest walidowane na pustość. Pusta nazwa przechodzi do INSERT/UPDATE. Frontend blokuje przez isValid, ale backend powinien bronić się niezależnie.
- **Fix**: Dodaj guard przed validate_languages w create i update: `if body.name.trim().is_empty() { return 422 }`
- **Decision**: FIXED — dodano guard w create i update w collections.rs

### F3 — Pole description w formularzu nigdy nie jest zapisywane

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Dimension**: Scope Discipline
- **Location**: CollectionFormScreen.kt:62, :147-160
- **Detail**: var description istnieje i renderuje OutlinedTextField, ale nigdy nie trafia do VM ani API. Użytkownik może wpisać opis, który jest cicho odrzucany. Backend nie ma kolumny description.
- **Fix**: Usuń pole description (stan + UI) z CollectionFormScreen.
- **Decision**: FIXED (zmiana zakresu) — zamiast usunięcia, zaimplementowano description end-to-end: migracja 004, models.rs, handlers, ApiModels.kt, Repository, ViewModel, CollectionFormScreen, App.kt

### F4 — collectionId!! w obsłudze usuwania

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Safety & Quality
- **Location**: CollectionFormScreen.kt:258
- **Detail**: viewModel.deleteCollection(collectionId!!) używa non-null assertion. Chroniony przez isEdit guard ale kruche.
- **Fix**: `val id = collectionId ?: return@onClick; viewModel.deleteCollection(id)`
- **Decision**: FIXED — zastosowano safe call w CollectionFormScreen.kt

### F5 — LanguageNames.entries zachowuje kolejność (potwierdzenie)

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Architecture
- **Location**: LangSelect.kt
- **Detail**: Plan wymaga kolejności pl/en/de/es/fr/it. LanguageNames to mapOf() (LinkedHashMap) — kolejność wstawiania gwarantowana. Poprawne, ale niejawne założenie.
- **Fix**: Dodano komentarz dokumentujący założenie kolejności wstawiania.
- **Decision**: FIXED — zaktualizowano KDoc w Flag.kt
