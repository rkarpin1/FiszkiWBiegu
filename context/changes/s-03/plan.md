# S-03: Weryfikacja produkcyjna — Plan implementacji

## Przegląd

Potwierdzenie, że pełny stos produkcyjny działa end-to-end: backend na Render.com dostępny bez cold startu (po upgrade planu), dane synchronizują się poprawnie, sesja nauki startuje z produkcyjnym backendem.

## Analiza stanu obecnego

- `ApiClient.kt:18` — backend URL hardcoded do `https://fiszki-w-biegu.onrender.com` (production-ready)
- Env vars na Render.com (`DATABASE_URL`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`) są skonfigurowane
- Google OAuth CLIENT_ID hardcoded w `MainActivity.kt:10`; backend waliduje token via `https://oauth2.googleapis.com/tokeninfo`
- Backend ma endpoint `/health` → `main.rs:14`
- S-02 zwalidowało pełny audio flow (TTS, Foreground Service, 30-min bieg) — żadne z tych komponentów nie wymagają ponownego testowania
- Render.com bezpłatny plan zasypia po 15 min nieaktywności → cold start 30-60 sek przy pierwszym żądaniu

## Pożądany stan końcowy

Backend na Render.com działa bez opóźnień cold start (plan upgraded), Rafał może zalogować się, zsynchronizować fiszki i uruchomić sesję nauki bez potrzeby "rozgrzewania" backendu.

## Czego NIE robimy

- Brak konfiguracji APK signing — debug APK wystarczy do użytku osobistego
- Brak pełnego 30-min biegu — S-02 to już zwalidowało
- Brak żadnych zmian w kodzie — S-03 to czysta weryfikacja operacyjna i upgrade infrastruktury
- Brak dystrybucji do innych użytkowników — walidacja tylko dla Rafała

## Podejście do implementacji

Zmiana operacyjna: upgrade planu Render + weryfikacja że pełny stack produkcyjny działa end-to-end. Debug APK (ten sam typ co S-02). Jedna faza.

---

## Faza 1: Upgrade Render i walidacja produkcyjna E2E

### Przegląd

Upgrade Render.com do płatnego planu (eliminacja cold start), weryfikacja health check bez opóźnień, pełny przepływ login → sync fiszek → start sesji nauki z produkcyjnym backendem.

### Wymagane zmiany:

#### 1. Upgrade planu Render

**Plik**: Panel Render.com (nie kod)

**Cel**: Przejść z bezpłatnego planu na Starter (~$7/mc), aby backend nie zasypiał po nieaktywności i nie wymuszał cold start 30-60 sek przed każdą sesją.

**Kontrakt**: Dashboard Render.com → usługa `fiszki-w-biegu-api` → Settings → Plan → upgrade do Starter.

#### 2. Weryfikacja health check

**Cel**: Potwierdzić że backend jest dostępny i odpowiada natychmiast po upgrade.

**Kontrakt**: `curl https://fiszki-w-biegu.onrender.com/health` → odpowiedź `ok` w < 3 sek.

#### 3. Build debug APK i install

**Plik**: `frontend/androidApp/build/outputs/apk/debug/`

**Cel**: Zbudować aktualny debug APK i zainstalować na urządzeniu testowym.

**Kontrakt**: `./gradlew :androidApp:assembleDebug` → APK zainstalowany via `adb install -r`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Health check bez cold start: `curl -w "%{time_total}" https://fiszki-w-biegu.onrender.com/health` — czas odpowiedzi < 3 sek
- Build bez błędów: `./gradlew :androidApp:assembleDebug`

#### Weryfikacja ręczna:

- Render plan upgraded: panel Render pokazuje plan Starter (lub wyższy)
- Login Google działa w produkcji: token walidowany przez backend, użytkownik zalogowany
- Collections widoczne: dane pobrane z bazy Supabase i wyświetlone w CollectionsScreen
- Flashcards dostępne: wejście w kolekcję pokazuje fiszki
- Sesja nauki startuje: „▶ Nauka" → LearningScreen → TTS zaczyna odtwarzać po polsku
- Brak crashy po starcie: `adb logcat -d | grep -i "crash\|fatal\|ANR\|exception" | head -20` — brak krytycznych błędów

---

## Strategia testowania

### Testy jednostkowe:

Brak — S-03 jest zmianą operacyjną; żaden kod nie jest zmieniany.

### Kroki testowania ręcznego:

Patrz Weryfikacja ręczna powyżej.

## Referencje

- ApiClient: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiClient.kt`
- Backend health check: `backend/src/main.rs:14`
- Render config: `render.yaml`
- GoogleSignInHelper: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/GoogleSignInHelper.kt`
- S-02 plan (archived): `context/archive/2026-05-27-s-02/plan.md`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Upgrade Render i walidacja produkcyjna E2E

#### Automatyczne

- [x] 1.1 Health check bez cold start: `curl` zwraca `ok` < 3 sek
- [x] 1.2 Build debug APK bez błędów: `./gradlew :androidApp:assembleDebug`

#### Ręczne

- [x] 1.3 Render plan upgraded (Starter lub wyższy)
- [x] 1.4 Login Google działa w produkcji
- [x] 1.5 Collections i flashcards widoczne (dane z Supabase)
- [x] 1.6 Sesja nauki startuje: TTS odtwarza po załadowaniu
- [x] 1.7 Brak crashy w logach ADB po starcie sesji
