# UI Reskin — Faza B: Reskin Ekranów — Krótki plan

> Pełny plan: `context/changes/ui-reskin-screens/plan.md`

## Co i dlaczego

Przenosimy wizualny design „Dawn Run" (z prototypu `.tmp/UI`) na 4 istniejące ekrany aplikacji
i wprowadzamy nawigację navigation3 z dolnym tab barem. Logika ViewModeli, repozytoriów i API
pozostaje bez zmian — faza B to czysto wizualna transformacja.

## Punkt wyjścia

Istnieje gotowy design system z S-04-A (`FiszkiColors`, czcionki, `Flag`, `MediaControls`, `TrackBar`).
Cztery ekrany (`LoginScreen`, `CollectionsScreen`, `FlashcardsScreen`, `LearningScreen`) używają
domyślnego Material3 bez themowania. `App.kt` ma prosty routing `var destination by remember`.

## Pożądany stan końcowy

Aplikacja wygląda jak projekt graficzny „Dawn Run": ciemne tła, czcionki Bricolage + JetBrains Mono,
paleta Ember/Peach. Dolny tab bar (Kolekcje / Konto) nawiguje przez navigation3. Cały flow biegowy
(login → nauka) działa bez regresji; nowe pola danych (progress, lastStudied) zaślepione lub ukryte.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---------|-------|---------------------|--------|
| Wiem/Nie wiem (StudyScreen) | Visible, `enabled=false` | PRD Non-Goal; widoczne jako zaślepka do S-04-D | Plan |
| Elapsed/Speed (StudyScreen) | Lokalny state w composable | Brak pola w LearningState; nie trafia do VM | Plan |
| LastUsedHero (CollectionsScreen) | Zawsze ukryty | `lastStudied` brak w CollectionDto; onboarding tile zamiast | Plan |
| Dialogi add/edit | Zostają jako AlertDialog | S-04-C zastąpi je ekranami; brak ryzyka regresji teraz | Plan |
| Tab bar | Pełny NavigationBar z ProfileScreen stub | Logout przenosi się z CollectionsScreen do Profilu | Plan |
| Theme ekranów | Wszystkie dark (`naturalDark=true`) | Spójność; light forms planowane w S-04-C | Plan |
| Detail hero / stats | Minimal + stats row stub (0%) | Zero nowych danych; count fiszek widoczny z `uiState` | Plan |
| Token mapping | Tabela w planie (t.ember→c.accent itd.) | `LocalFiszkiTokens` ≠ `LocalFiszkiColors` — 8 reguł | Plan |
| Akcent kolekcji | `accentColorForId(id)` z Color.kt | Deterministyczny kolor bez DB; zaimplementowane w S-04-A | Plan |
| Nawigacja | navigation3-compose (BackStack/NavDisplay) | Już w `commonMain.dependencies`; KMP-native | Plan |

## Zakres

**W zakresie:**
- Reskin `LoginScreen.kt` → AuthScreen design
- Reskin `CollectionsScreen.kt` → CollectionsScreenB design
- Reskin `FlashcardsScreen.kt` → CollectionDetailScreen design
- Reskin `LearningScreen.kt` → StudyScreenB design (zaślepki jak wyżej)
- `App.kt` → navigation3 BackStack/NavDisplay + NavigationBar
- `ProfileScreen.kt` (nowy) — stub: avatar, Wyloguj

**Poza zakresem:**
- `CollectionFormScreen`, `CardFormScreen` (S-04-C)
- Realne dane `progress`, `lastStudied`, `displayName`, streak (S-04-D)
- Apple/Facebook Sign-In (poza MVP)
- TranslateService (S-04-D)
- Light theme (S-04-C dla form screens)

## Architektura / Podejście

Każdy ekran dostaje `FiszkiThemedScreen(naturalDark = true)` jako root wrapper zamiast `Scaffold`
z domyślnym MaterialTheme. Komponenty z S-04-A (`Flag`, `CapsLabel`, `TrackBar`, `MediaControls`)
są używane bezpośrednio. Mapowanie tokenów `t.xxx → c.xxx` jest jednorazową adaptacją podczas
przepisywania; tabela w planie eliminuje zgadywanie. Phase 2 przepisuje App.kt na navigation3 —
`sealed Route` zastępuje `sealed Destination`, `NavDisplay` zastępuje `when(dest)`, `BackStack.add/removeLastOrNull/clear` zastępują `destination = ...`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. Reskin 4 ekranów | Wszystkie ekrany w dark design Dawn Run | App.kt nie kompiluje się po usunięciu `onLogout` z CollectionsScreen — świadomy stan do Phase 2 |
| 2. App.kt nav3 + ProfileScreen | Działająca nawigacja, tab bar, ProfileScreen | Nieznana dokładna API navigation3 1.1.1 — implementator musi zweryfikować importy |

**Wymagania wstępne:** S-04-A ukończone (design system, komponenty, `FiszkiThemedScreen`)
**Szacowany nakład pracy:** ~2-3 sesje w 2 fazach

## Otwarte ryzyka i założenia

- **navigation3 API**: Dokładne klasy/pakiety dla `rememberNavBackStack`, `NavDisplay`, `entryProvider` nie były wcześniej używane w projekcie — należy zweryfikować z artefaktu `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`
- **Broken build w Phase 1**: Po usunięciu `onLogout` z `CollectionsScreen`, `App.kt` nie kompiluje się do czasu Phase 2 — to świadomy stan przejściowy

## Kryteria sukcesu (podsumowanie)

- `./gradlew :androidApp:assembleDebug` — BUILD SUCCESSFUL po Phase 2
- APK instaluje się, pełny flow biegowy działa (login → kolekcje → nauka)
- Dolny tab bar nawiguje między Kolekcjami a Kontem; sesja nauki odtwarza TTS bez regresji
