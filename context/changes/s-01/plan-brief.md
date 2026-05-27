/# Zarządzanie kolekcjami i fiszkami E2E — Krótki plan

> Pełny plan: `context/changes/s-01/plan.md`

## Co i dlaczego

S-01 domyka przepływ CRUD kolekcji i fiszek: uzupełnia 3 luki UI (rename kolekcji, logout, Retry przy błędzie), a następnie weryfikuje cały flow E2E na fizycznym urządzeniu Android. Backend i 90% UI są już gotowe — to ostatni krok przed przekazaniem S-01 jako ukończonego.

## Punkt wyjścia

Backend działa (`https://fiszki-w-biegu.onrender.com`, env vars ustawione). `FlashcardsScreen` ma pełny CRUD. `CollectionsScreen` ma C, R, D — brak rename i logout. Auto-login (`authRepository.isLoggedIn()` w `App.kt:30`) działa. `CollectionRepository.rename()` i `PUT /collections/{id}` są gotowe, ale bez UI.

## Pożądany stan końcowy

Użytkownik na fizycznym telefonie Android może wykonać pełny przepływ: zaloguj przez Google → zarządzaj kolekcjami (twórz, edytuj nazwę, usuń) → zarządzaj fiszkami (twórz, edytuj, usuń) → wyloguj się. Przy błędzie sieciowym widoczny przycisk Retry.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---------|-------|---------------------|--------|
| Rename kolekcji w S-01 | TAK | FR-004 wymaga edycji; backend gotowy, tylko UI brakuje | Plan |
| Logout | Przycisk w TopAppBar CollectionsScreen | `authRepository.logout()` gotowe; `App.kt` ma `authRepository` in scope | Plan |
| Retry przy błędzie | Przycisk obok komunikatu błędu | Render cold start ~30s; UX wymaga możliwości ponowienia | Plan |
| webClientId refaktor | Nie — tech debt | Niskie ryzyko pozostawienia; nie blokuje S-01 | Plan |
| Weryfikacja | Ręczna na fizycznym urządzeniu | Google Sign-In przez Credential Manager nie działa niezawodnie na emulatorze | Plan |
| Startup auth check | Już zaimplementowany | `App.kt:30` — `isLoggedIn()` → skip do Collections | Kod |

## Zakres

**W zakresie:**
- Rename kolekcji — `EditCollectionDialog` + edit button w `CollectionItem`
- Logout — `onLogout` callback z `App.kt`, ikona w `TopAppBar` CollectionsScreen
- Retry button — `CollectionsScreen` i `FlashcardsScreen` przy błędzie
- Build debug APK + ręczny E2E test (10-punktowa checklista)

**Poza zakresem:**
- Refaktor `webClientId` do `strings.xml` (tech debt)
- Testy automatyczne (unit/integration)
- Offline cache (F-01)
- Pozycjonowanie fiszek, statystyki, wielojęzyczność

## Architektura / Podejście

Trzy niezależne zmiany UI w Fazie 1 — każda może być commitowana osobno. Logout przez callback (`onLogout: () -> Unit`) do `CollectionsScreen` z `App.kt` — nie wymaga dodawania `AuthRepository` do `CollectionsViewModel`. Retry przez wywołanie istniejących metod `viewModel.loadCollections()` / `viewModel.loadFlashcards()`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. Uzupełnienie UI | Rename kolekcji, logout, Retry przy błędzie; build przechodzi | Ikona `Icons.AutoMirrored.Filled.Logout` dostępna w Material3 1.11 — weryfikacja przy build |
| 2. Weryfikacja E2E | 10-punktowa checklista przeszła na fizycznym urządzeniu | Google Sign-In wymaga konta Google skonfigurowanego na urządzeniu |

**Wymagania wstępne:** Fizyczne urządzenie Android (minSdk = 30, Android 11+) z kontem Google, ADB lub Android Studio do instalacji APK.  
**Szacowany nakład pracy:** ~2 sesje (Faza 1: ~1 sesja; Faza 2: ~0.5 sesji + test).

## Otwarte ryzyka i założenia

- `Icons.AutoMirrored.Filled.Logout` może nie być dostępna w compose-material3 1.11-alpha — fallback: `Icons.Default.ExitToApp`
- Render cold start ~30s przy pierwszym żądaniu po przerwie — Retry button bezpośrednio adresuje ten przypadek

## Kryteria sukcesu (podsumowanie)

- Build APK bez błędów (`./gradlew :androidApp:assembleDebug`)
- Wszystkie 10 punktów checklisty E2E przeszły na fizycznym urządzeniu Android
- Brak regresji w istniejącym flow (delete z potwierdzeniem, tworzenie fiszek)
