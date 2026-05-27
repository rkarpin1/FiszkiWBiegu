<!-- PLAN-REVIEW-REPORT -->
# Przegląd planu: Zarządzanie kolekcjami i fiszkami — weryfikacja E2E

- **Plan**: `context/changes/s-01/plan.md`
- **Tryb**: Głęboki
- **Data**: 2026-05-27
- **Werdykt**: DO POPRAWY → SOLIDNY (po poprawkach)
- **Ustalenia**: 2 krytyczne, 2 ostrzeżenia, 0 obserwacji

## Werdykty

| Wymiar | Werdykt |
|--------|---------|
| Zgodność ze stanem końcowym | ZALICZONY |
| Oszczędna realizacja | ZALICZONY |
| Dopasowanie architektoniczne | NIEZALICZONY |
| Martwe punkty | ZALICZONY |
| Kompletność planu | NIEZALICZONY |

## Ugruntowanie

Grounding: 5/5 ścieżek ✓, 4/4 symboli ✓, brief↔plan ✓

Zweryfikowane pliki kodu: CollectionsViewModel.kt, CollectionsScreen.kt, FlashcardsScreen.kt, App.kt, CollectionRepository.kt, shared/build.gradle.kts

## Ustalenia

### F1 — Kontrakt Retry odsyłał do nieistniejącego SnackbarHostState

- **Waga**: ❌ KRYTYCZNE
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Dopasowanie architektoniczne
- **Lokalizacja**: Faza 1 — sekcje 5 i 6
- **Szczegóły**: Plan mówił „snackbarHostState.showSnackbar(...) actionLabel". Oba ekrany używają standalone `Snackbar` composable renderowanego w `Box` (CollectionsScreen:82-86, FlashcardsScreen:105-109) — bez SnackbarHostState, bez LaunchedEffect. Implementator napisałby kod, który się nie kompiluje.
- **Poprawka zastosowana**: Kontrakt zmieniony na parametr `action` standalone Snackbar (`action = { TextButton(onClick = { viewModel.loadCollections() }) { Text("Ponów") } }`)
- **Decyzja**: NAPRAWIONO

---

### F2 — Niezgodność nazwy Fazy 2 między treścią a Progress

- **Waga**: ❌ KRYTYCZNE
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Kompletność planu
- **Lokalizacja**: Treść: `## Faza 2: Build i weryfikacja E2E na urządzeniu` / Progress: `### Faza 2: Weryfikacja E2E`
- **Szczegóły**: Kontrakt mechaniczny `/10x-implement` wymaga dokładnego dopasowania nazw faz między treścią planu a sekcją Progress.
- **Poprawka zastosowana**: Progress zmieniony na `### Faza 2: Build i weryfikacja E2E na urządzeniu`
- **Decyzja**: NAPRAWIONO

---

### F3 — Przycisk edycji: IconButton+Icon vs TextButton; brak icons w build.gradle

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🔎 ŚREDNI — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Wymiar**: Dopasowanie architektoniczne
- **Lokalizacja**: Faza 1 — sekcja 2 (Rename UI), CollectionItem
- **Szczegóły**: Plan używa `IconButton(Icons.Default.Edit)` w CollectionItem, ale istniejący wzorzec to `TextButton("Usuń")` (CollectionItem:109) i `TextButton("Edytuj")` (FlashcardItem:132). `shared/build.gradle.kts` nie zawiera zależności icons (`material-icons-core`) — zero użyć `Icon/Icons.*` w całej bazie kodu.
- **Poprawka zastosowana (B)**: Zachowano `IconButton+Icons.Default.Edit`, ale dodano ostrzeżenie w planie: jeśli `Icon(Icons.Default.Edit)` nie kompiluje się, należy dodać `org.jetbrains.compose.material:material-icons-core:1.11.0` lub zamienić na `TextButton("Edytuj")`
- **Decyzja**: NAPRAWIONO (Poprawka B)

---

### F4 — Krok E2E #1 brak w sekcji Progress Fazy 2

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Kompletność planu
- **Lokalizacja**: Progress — Faza 2
- **Szczegóły**: Tabela E2E ma 10 kroków (#1-#10). Progress śledził 2.2-2.10 = 9 elementów. Krok #1 ("Uruchom aplikację z wyczyszczonymi danymi → LoginScreen") nie miał pozycji. Kryteria sukcesu wymagają "Wszystkich 10 punktów checklisty".
- **Poprawka zastosowana**: Dodano `2.2 Aplikacja uruchomiona ze wyczyszczonymi danymi → LoginScreen`, przenumerowano 2.2-2.10 → 2.3-2.11
- **Decyzja**: NAPRAWIONO

---

## Podsumowanie zmian w planie

Cztery edycje w `context/changes/s-01/plan.md`:
1. Sekcje 5 i 6: kontrakt Retry zmieniony z `snackbarHostState.showSnackbar(actionLabel)` na `Snackbar(action = { TextButton(...) })`
2. Progress `### Faza 2`: nazwa uzgodniona z treścią planu
3. Sekcja 2 kontrakt: dodano ostrzeżenie o brakującej zależności icons
4. Progress Faza 2: dodano item 2.2 (krok #1 E2E), przenumerowano 2.2-2.10 → 2.3-2.11
