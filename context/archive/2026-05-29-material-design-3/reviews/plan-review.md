<!-- PLAN-REVIEW-REPORT -->
# Przegląd planu: Material Design 3 Compatibility

- **Plan**: context/changes/material-design-3/plan.md
- **Tryb**: Głęboki
- **Data**: 2026-05-29
- **Werdykt**: DO POPRAWY → po poprawkach: SOLIDNY
- **Ustalenia**: 0 krytycznych, 1 ostrzeżenie, 2 obserwacje

## Werdykty

| Wymiar | Werdykt |
|--------|---------|
| Zgodność ze stanem końcowym | ZALICZONY |
| Oszczędna realizacja | OSTRZEŻENIE |
| Dopasowanie architektoniczne | ZALICZONY |
| Martwe punkty | ZALICZONY |
| Kompletność planu | OSTRZEŻENIE |

## Ugruntowanie

5/5 ścieżek ✓, symbole (LocalFiszkiColors, darkColorScheme, FiszkiColors) ✓, brief↔plan ✓

## Ustalenia

### F1 — Mapowanie surface2→surfaceVariant jest semantycznie błędne

- **Waga**: ⚠️ OSTRZEŻENIE
- **Wpływ**: 🔎 ŚREDNI — prawdziwy kompromis; zatrzymaj się, aby to przemyśleć
- **Wymiar**: Oszczędna realizacja
- **Lokalizacja**: Kluczowe odkrycia + Kontrakt Fazy 1 + instrukcje Fazy 2/3
- **Szczegóły**: Theme.kt:93 już ustawia `surface = Bg2` (= FiszkiColors.surface2). Plan proponował `surfaceVariant = Bg2` (ten sam kolor) tworząc duplikat. Poprawne: surface2→scheme.surface (już jest), surface3→scheme.surfaceVariant (Bg3).
- **Fix A ⭐ Zastosowana**: Zmieniono mapowanie — surface2→surface, surface3→surfaceVariant; surfaceVariant = Bg3/Cream2; surface3 usunięte z listy "zostaje w LocalFiszkiColors".
- **Decyzja**: NAPRAWIONE via Fix A

### F2 — Brak pozycji 3.5 w Progress dla Fazy 3

- **Waga**: OBSERWACJA
- **Wpływ**: 🏃 NISKI — szybka decyzja; oczywista poprawka
- **Wymiar**: Kompletność planu
- **Lokalizacja**: ## Postęp → Faza 3 → Ręczne
- **Szczegóły**: Faza 3 miała 3 kryteria ręczne w treści, ale tylko 2 w Progress.
- **Fix**: Dodano `- [ ] 3.5 Formularze, dialogi, listy — brak artefaktów wizualnych`
- **Decyzja**: NAPRAWIONE

### F3 — Flag.kt nie wymieniony w Fazie 2

- **Waga**: OBSERWACJA
- **Wpływ**: 🏃 NISKI — szybka decyzja
- **Wymiar**: Kompletność planu
- **Lokalizacja**: Faza 2 — Wymagane zmiany
- **Szczegóły**: Flag.kt używa `RoundedCornerShape(size * 0.18f)` — proporcjonalny, nie mapuje na MD3 token. Nie używa LocalFiszkiColors.
- **Fix**: Dodano punkt 4 w Fazie 2 z notą "zostaw bez zmian — kształt proporcjonalny".
- **Decyzja**: NAPRAWIONE
