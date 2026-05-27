# S-03: Weryfikacja produkcyjna — Krótki plan

> Pełny plan: `context/changes/s-03/plan.md`

## Co i dlaczego

Potwierdzenie, że pełny stos produkcyjny działa end-to-end: backend na Render.com jest dostępny bez cold startu, dane synchronizują się z Supabase, a Rafał może zalogować się i uruchomić sesję nauki z produkcyjnym backendem. To formalne zamknięcie MVP — gwiazdę przewodnią projektu.

## Punkt wyjścia

S-02 zwalidowało cały audio flow (TTS, Foreground Service, sterowanie słuchawkami, 30-min bieg). Backend URL w aplikacji już wskazuje na `https://fiszki-w-biegu.onrender.com`, env vars są skonfigurowane. Jedyna rzecz do naprawienia: Render.com bezpłatny plan zasypia po 15 min nieaktywności — cold start 30-60 sek, nieakceptowalny przed biegiem.

## Pożądany stan końcowy

Rafał otwiera aplikację, loguje się przez Google, widzi swoje fiszki i uruchamia sesję nauki — bez czekania na rozgrzanie backendu, bez crashy, z danymi produkcyjnymi.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---|---|---|---|
| Typ APK | Debug APK | Wystarczy do użytku osobistego Rafała; brak potrzeby keystore i signing config | Plan |
| Cold start | Upgrade Render do Starter | Eliminuje 30-60 sek opóźnienie bez zmian w kodzie | Plan |
| Zakres | Tylko Rafał | To jest walidacja MVP, nie launch dla wielu użytkowników | Plan |
| Kod | Zero zmian | S-02 zwalidowało wszystko co wymagało kodu; S-03 to zmiana operacyjna | Plan |
| 30-min bieg | Pominięty | S-02 już to zrobiło; zbędne powtórzenie | Plan |

## Zakres

**W zakresie:**
- Upgrade planu Render.com (Starter)
- Weryfikacja health check < 3 sek
- Pełny E2E smoke: login → collections → flashcards → start sesji

**Poza zakresem:**
- APK signing / release build
- Dystrybucja do innych użytkowników
- Zmiany w kodzie
- Kolejny 30-min bieg (S-02 to pokrył)

## Architektura / Podejście

Zmiana czysto operacyjna. Backend URL już wskazuje na produkcję (`ApiClient.kt:18`). Upgrade Render = dashboard action, zero kodu. Walidacja E2E = te same kroki co S-02 Faza 1, ale z produkcyjnym backendem jako głównym przedmiotem weryfikacji.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|---|---|---|
| 1. Upgrade Render + walidacja E2E | Backend bez cold start; pełny flow login → nauka działa | Env vars mogą być złe pomimo potwierdzenia — odkryjemy przy próbie loginu |

**Wymagania wstępne:** S-02 done, dostęp do panelu Render.com, urządzenie Android z ADB  
**Szacowany nakład pracy:** < 1 sesja; głównie czas kliknięć w dashboard + test manualny

## Otwarte ryzyka i założenia

- Env vars są skonfigurowane (potwierdzone przez użytkownika) — ale token mismatch między `MainActivity.kt` CLIENT_ID a `GOOGLE_CLIENT_ID` w Render ujawni się dopiero przy próbie loginu
- Render Starter plan eliminuje cold start — do potwierdzenia po upgrade

## Kryteria sukcesu (podsumowanie)

- Backend odpowiada < 3 sek (curl /health po upgrade)
- Rafał loguje się przez Google i widzi swoje fiszki
- Sesja nauki startuje z TTS — bez crashy w logach ADB
