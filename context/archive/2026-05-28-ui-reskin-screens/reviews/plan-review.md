<!-- PLAN-REVIEW-REPORT -->
# Przegląd planu: UI Reskin — Faza B: Reskin Istniejących Ekranów

- **Plan**: `context/changes/ui-reskin-screens/plan.md`
- **Tryb**: Głęboki
- **Data**: 2026-05-28
- **Werdykt**: DO POPRAWY
- **Ustalenia**: 1 krytyczne | 1 ostrzeżenie | 1 obserwacja

## Werdykty

| Wymiar | Werdykt |
|--------|---------|
| Zgodność ze stanem końcowym | ZALICZONY |
| Oszczędna realizacja | ZALICZONY |
| Dopasowanie architektoniczne | ZALICZONY |
| Martwe punkty | OSTRZEŻENIE |
| Kompletność planu | NIEZALICZONY |

## Ugruntowanie

5/5 ścieżek ✓, 4/4 symboli ✓, brief↔plan ✓

## Ustalenia

### F1 — Kryterium sukcesu Phase 1 nieosiągalne — sprzeczność kompilacji

- **Waga**: ❌ KRYTYCZNE
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Kompletność planu
- **Lokalizacja**: Faza 1 — CollectionsScreen kontrakt + Kryteria sukcesu
- **Szczegóły**: Plan usuwał `onLogout` z CollectionsScreen w Phase 1, ale App.kt:70 (ten sam moduł commonMain) wciąż go wywoływał. `:shared:wasmJsMainClasses` kompiluje całe commonMain razem — wliczając App.kt — co powoduje błąd kompilacji i sprawia, że kryterium Phase 1 jest nieosiągalne. Plan błędnie modelował ten problem jako dotyczący tylko `:androidApp:assembleDebug`.
- **Poprawka A ⭐ Zalecana**: Zostaw `onLogout: () -> Unit` w sygnaturze CollectionsScreen w Phase 1 (parametr nieużywany wewnętrznie — brak przycisku Wyloguj). Phase 2 usuwa go gdy App.kt jest przepisywany.
  - Siła: App.kt bez zmian w Phase 1; obie weryfikacje automatyczne przechodzą; APK instalowalny po Phase 1.
  - Kompromis: Nieużywany parametr przez jeden commit — akceptowalne przejściowo.
  - Pewność: WYSOKA — potwierdzone przez CollectionsScreen.kt:38-40 i App.kt:70.
  - Martwy punkt: Brak znaczących.
- **Poprawka B**: Dodaj aktualizację call site App.kt do Phase 1.
  - Siła: Parametr znika kompletnie w jednym commicie.
  - Kompromis: Narusza "App.kt bez zmian"; assembleDebug dalej nie przejdzie.
  - Pewność: ŚREDNIA.
  - Martwy punkt: Brak znaczących.
- **Decyzja**: ZAAKCEPTOWANO (Poprawka A) — plan zaktualizowany

### F2 — navigation3 API niezweryfikowane; brak kroku walidacji przed Phase 2

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🔎 ŚREDNI — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Wymiar**: Martwe punkty
- **Lokalizacja**: Faza 2 — App.kt kontrakt, "Uwaga implementacyjna"
- **Szczegóły**: Plan poprawnie flaguje ryzyko nieznanych importów (`rememberNavBackStack`, `NavDisplay`, `entryProvider`), ale brakuje formalnego kroku weryfikacyjnego przed główną implementacją Phase 2. Implementator wchodzi w całe przepisanie App.kt bez potwierdzenia, że API wygląda jak w planie. Dodatkowe ryzyko: jeśli BackStack opakowuje trasy w `NavEntry<T>`, to `backStack.lastOrNull() is Route.Collections` nigdy nie będzie true i tab bar nie zadziała.
- **Poprawka**: Dodaj krok `2.0 Weryfikacja navigation3 API` do Progress Phase 2 i odpowiadającą "Weryfikację wstępną" w kryteriach.
- **Decyzja**: ZAAKCEPTOWANO — plan zaktualizowany (dodano krok 2.0 i sekcję "Weryfikacja wstępna")

### F3 — Tab bar callback — ambiwalentne "lub" w opisie

- **Waga**: ℹ️ OBSERWACJA
- **Wpływ**: 🏃 NISKI — szybka decyzja; poprawka jest oczywista i wąsko zakrojona
- **Wymiar**: Kompletność planu
- **Lokalizacja**: Faza 2 — App.kt kontrakt, Tab bar callbacks
- **Szczegóły**: `onCollections: backStack.removeLastOrNull() jeśli jesteśmy na Profile (lub backStack.add(Route.Collections))` — "lub" nie definiuje kiedy która ścieżka.
- **Poprawka**: Zastąp ambiguitę konkretną logiką: `if (currentRoute is Route.Profile) backStack.removeLastOrNull()` / `if (currentRoute !is Route.Profile) backStack.add(Route.Profile)`.
- **Decyzja**: ZAAKCEPTOWANO — plan zaktualizowany
