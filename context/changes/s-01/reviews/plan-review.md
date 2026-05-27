<!-- PLAN-REVIEW-REPORT -->
# Przegląd planu: Zarządzanie kolekcjami i fiszkami — weryfikacja E2E

- **Plan**: context/changes/s-01/plan.md
- **Tryb**: Szybki (retrospektywny — plan już zaimplementowany)
- **Data**: 2026-05-27
- **Werdykt**: SOLIDNY (po sortowaniu)
- **Ustalenia**: 0 krytycznych  1 ostrzeżenie  1 obserwacja

## Werdykty

| Wymiar | Werdykt |
|--------|---------|
| Zgodność ze stanem końcowym | ZALICZONY |
| Oszczędna realizacja | ZALICZONY |
| Dopasowanie architektoniczne | ZALICZONY |
| Martwe punkty | OSTRZEŻENIE |
| Kompletność planu | OSTRZEŻENIE |

## Ugruntowanie

4/4 ścieżek ✓, 3/3 symboli ✓, brief↔plan ✓

## Ustalenia

### F1 — Brak ścieżki 401 → wylogowanie przy wygaśnięciu tokenu

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🔎 ŚREDNI — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Wymiar**: Martwe punkty
- **Lokalizacja**: ApiClient.kt — brak interceptora
- **Szczegóły**: Plan zakładał działający flow auth, ale nie uwzględnił scenariusza wygaśnięcia tokenu (30-dniowy JWT). Gdy token wygaśnie, każde wywołanie API zwróci 401, a `requireToken()` rzuca `IllegalStateException("Not authenticated")` — błąd trafia do Snackbar zamiast triggerować wylogowanie. Użytkownik utknął w pętli błędów bez możliwości odblokowania bez ręcznego czyszczenia danych.
- **Decyzja**: NAPRAWIONE via Poprawka A — nowa zmiana s-02a utworzona (Ktor 401 interceptor → automatyczne wylogowanie)

---

### F2 — Kontrakt `requestDelete` nie specyfikuje przechowywania nazwy

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Kompletność planu
- **Lokalizacja**: CollectionsViewModel.kt:73, FlashcardsViewModel.kt:68
- **Szczegóły**: `DeleteConfirmationDialog` otrzymuje nazwę przez `uiState.collections.find { it.id == id }?.name.orEmpty()` w czasie renderowania. Jeśli lista kolekcji zostanie odświeżona między `requestDelete` a renderowaniem dialogu (rzadkie, ale możliwe), dialog pokaże pustą nazwę. Bezpieczniejszy wzorzec to `pendingDeleteName: String?` przechowywany w stanie razem z `pendingDeleteId`.
- **Decyzja**: POMINIĘTE — ryzyko jest akceptowalne w MVP; scenariusz praktycznie niemożliwy przy aktualnym flow
