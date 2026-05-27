# S-02: Kompletna sesja nauki audio offline — Plan implementacji

## Przegląd

Weryfikacja E2E pełnego przepływu sesji nauki audio: TTS, Foreground Service, sterowanie słuchawkami, praca z ekranem wyłączonym przez 30+ minut. Rdzeń funkcjonalności (TTS loop, MediaSession, screen-off, pętla powrotu) jest już zaimplementowany — ta zmiana potwierdza, że działa end-to-end na prawdziwym urządzeniu, i naprawia wszystkie błędy odkryte podczas testów przed zamknięciem zmiany.

## Analiza stanu obecnego

Wszystkie wymagania funkcjonalne PRD FR-010–FR-014 mają implementację:

- **TTS loop** — `LearningService.playLoop()`: PL (full vol) → EN silent (duration capture) → 800ms → 3×EN (full vol, +500ms) → 1000ms → next card
- **Foreground Service** — `LearningService extends MediaSessionService`, `foregroundServiceType="mediaPlayback"`, pełne uprawnienia
- **Screen-off** — Foreground Service z persistent notification utrzymuje TTS niezależnie od UI lifecycle
- **Headphone controls** — Media3 auto-routing: `TtsPlayer.onSeekToNext/Previous/onPlayWhenReadyChanged` → `LearningService.next/previous/pause/resume`
- **Pętla powrotu** — `currentIndex = (currentIndex + 1) % flashcards.size` (LearningService:137)
- **Offline po starcie** — serwis trzyma fiszki w pamięci; F-01 blokuje start gdy offline
- **Notification z kontrolkami** — `LearningNotificationProvider`: PL/EN tekst, kolory faz, przyciski ⏮▶⏸⏭

Jedyne potwierdzone ryzyko: integracja kilku subsystemów (TTS init race, MediaController latency, wakelock na 30+ min) może ujawnić błędy niewidoczne w unit testach.

## Pożądany stan końcowy

Po zakończeniu tego planu: Rafał może uruchomić aplikację, wybrać kolekcję, nacisnąć „▶ Nauka", włożyć telefon do kieszeni i słuchać fiszek przez cały 30-minutowy bieg — sterując sesjąprzyciskami słuchawek Bluetooth, bez dostępu do internetu, bez crashy, bez zawieszenia TTS.

### Kluczowe odkrycia:

- `LearningService.kt:105` — TTS readiness polling: `while (!ttsReady) delay(100)` — jeśli TTS init zawiedzie, pętla wisi; brak timeoutu
- `LearningService.kt:127` — 3 powtórzenia EN, `+500ms` delay — wartości do weryfikacji empirycznej
- `AndroidLearningController.kt:34-38` — `MediaController.buildAsync()` — kontroler może nie być gotowy gdy user natychmiast naciska headphone; warto zweryfikować
- Historia commitów: "bugfix drugiego wejścia w tryb nauki" — second-entry scenario do jawnego przetestowania
- Brak audio focus handlingu — świadoma decyzja, poza zakresem MVP

## Czego NIE robimy

- Brak implementacji "ostatniej używanej kolekcji" — poza zakresem tej zmiany
- Brak obsługi audio focus (duck/pause przy połączeniu, Spotify) — poza zakresem MVP (PRD FR-010–FR-014 tego nie wymaga)
- Brak nowego UI w LearningScreen — ekran jest wystarczający
- Brak zmiany hardkodowanych czasów pauz — aktualne wartości (800/500/1000ms) weryfikujemy empirycznie; zmieniamy tylko jeśli rzeczywiście nie działają podczas biegu
- Brak testów jednostkowych dla TTS/Service — testowanie z mock ConnectivityManager/TTS poza zakresem MVP

## Podejście do implementacji

Zmiana jest weryfikacyjna: przechodzimy przez kompletną listę scenariuszy testowych w dwóch fazach. Faza 1 to szybkie smoke testy na urządzeniu/emulatorze (scenariusze podstawowe, przewidywalne). Faza 2 to full-run test z prawdziwymi słuchawkami Bluetooth i telefonem w kieszeni przez 30+ minut. Code fixy trafiają do tej samej zmiany — `s-02` zamykamy dopiero gdy oba etapy przechodzą. Każdy fix dostaje własny commit przed kolejnym testem.

---

## Faza 1: Smoke test — scenariusze podstawowe

### Przegląd

Weryfikacja wszystkich scenariuszy E2E w kontrolowanym środowisku (urządzenie podłączone do komputera, ADB dostępne). Cel: potwierdzić że cała ścieżka działa i nie ma crashy przy normalnym użyciu, zanim przejdziemy do testu w warunkach biegu.

### Wymagane zmiany:

#### 1. Build APK i install

**Plik**: `frontend/androidApp/build/outputs/apk/debug/`

**Cel**: Zbudować aktualny debug APK i zainstalować na urządzeniu testowym. Weryfikuje że projekt kompiluje się bez błędów.

**Kontrakt**: `./gradlew :androidApp:assembleDebug` → APK installed via `adb install -r`

#### 2. Code-fix (warunkowe)

**Plik**: dowolny plik wskazany przez błąd wykryty w scenariuszach poniżej

**Cel**: Naprawić błędy odkryte podczas smoke testu. Każdy fix jest commitowany przed przejściem do kolejnego scenariusza.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Kompilacja bez błędów: `./gradlew :androidApp:assembleDebug`
- Brak błędów Koin w logach po starcie: `adb logcat | grep -i "koin\|error\|crash" | head -30`

#### Weryfikacja ręczna:

- Scenariusz podstawowy: app uruchomiona → Collections → wybrać kolekcję z fiszkami → FlashcardsScreen → „▶ Nauka" aktywny (online) → LearningScreen → TTS zaczyna mówić po polsku
- Cykl audio: jedno słowo PL pełnym głosem → EN cicho → 800ms przerwa → EN 3× pełnym głosem z przerwami → następna fiszka
- Pauza/wznowienie: naciśnij ⏸ → TTS zatrzymuje się; naciśnij ▶ → TTS wznawia od tej samej fiszki
- Nawigacja: ⏭ przechodzi do następnej fiszki; ⏮ cofa do poprzedniej; po ostatniej fiszce ⏭ wraca do pierwszej
- Drugi wход do trybu nauki: Stop → wróć do FlashcardsScreen → ▶ Nauka → nowa sesja startuje poprawnie (brak „zmrożonego" stanu z poprzedniej)
- Offline guard: włącz tryb samolotowy → „▶ Nauka" wyłączony; wyłącz → przycisk aktywny

---

## Faza 2: Full-run test — 30+ minut z słuchawkami Bluetooth, ekran off

### Przegląd

Test w warunkach zbliżonych do rzeczywistego biegu: prawdziwe słuchawki Bluetooth, telefon w kieszeni, ekran wyłączony przez minimum 30 minut. Weryfikuje stabilność TTS, wakelock, MediaSession routing i brak crashy w tle.

### Wymagane zmiany:

#### 1. Code-fix (warunkowe)

**Plik**: dowolny plik wskazany przez błąd wykryty w scenariuszach poniżej

**Cel**: Naprawić błędy stabilności lub integracji odkryte podczas long-run testu. Każdy fix jest commitowany przed retestem.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Po zakończeniu biegu: `adb logcat -d | grep -i "crash\|fatal\|ANR\|exception" | head -20` — brak krytycznych błędów w logach

#### Weryfikacja ręczna:

- Start sesji z słuchawkami: uruchom sesję nauki → podłącz słuchawki Bluetooth → TTS słyszalny w słuchawkach
- Screen-off: wyłącz ekran telefonu → TTS kontynuuje bez przerwy (brak pauz/zawieszeń)
- Sterowanie z słuchawek (ekran off): przycisk PLAY/PAUSE → pauza/wznowienie; przycisk NEXT → następna fiszka; przycisk PREV → poprzednia
- Notification controls: rozwiń notification shade → przyciski ⏮⏸⏭ działają; tekst PL/EN aktualizuje się; kolor zmienia się z PL (czerwony) na EN (niebieski)
- 30-minutowy bieg: uruchom sesję → ekran off → zablokuj telefon → biegaj 30+ minut → po powrocie: TTS nadal gra, brak crashy, wskaźnik fiszek aktualny
- Pełna pętla kolekcji: odczekaj aż wszystkie fiszki zostaną odtworzone → sesja wraca do pierwszej fiszki bez przerwy
- Po 30 min: sprawdź logi ADB na crashy lub ANR

---

## Strategia testowania

### Testy jednostkowe:

- Brak nowych testów jednostkowych — TTS, MediaSession i Foreground Service wymagają androidowego środowiska uruchomieniowego; mockowanie nie oddałoby rzeczywistego zachowania (cel tej zmiany).

### Kroki testowania ręcznego:

Scenariusze pełne — patrz Faza 1 i Faza 2 Weryfikacja ręczna powyżej.

## Referencje

- LearningService: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt`
- TtsPlayer: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt`
- AndroidLearningController: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/AndroidLearningController.kt`
- LearningNotificationProvider: `frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningNotificationProvider.kt`
- LearningScreen: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningScreen.kt`
- LearningViewModel: `frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningViewModel.kt`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dodaj ` — <commit sha>`, gdy krok zostanie zrealizowany.

### Faza 1: Smoke test — scenariusze podstawowe

#### Automatyczne

- [x] 1.1 Kompilacja bez błędów: `./gradlew :androidApp:assembleDebug` — 941e77b
- [x] 1.2 Brak błędów Koin w logach po starcie — 941e77b

#### Ręczne

- [x] 1.3 Scenariusz podstawowy: app → Collections → fiszki → LearningScreen → TTS startuje — 941e77b
- [x] 1.4 Cykl audio: PL full → EN silent → 3×EN full z przerwami → następna fiszka — 941e77b
- [x] 1.5 Pauza/wznowienie działa poprawnie — 941e77b
- [x] 1.6 Nawigacja ⏭/⏮ działa; pętla powrotu po ostatniej fiszce — 941e77b
- [x] 1.7 Drugi wход do trybu nauki działa poprawnie (brak zamrożonego stanu) — 941e77b
- [x] 1.8 Offline guard: tryb samolotowy wyłącza „▶ Nauka" — 941e77b

### Faza 2: Full-run test — 30+ min z słuchawkami, ekran off

#### Automatyczne

- [x] 2.1 Brak krytycznych błędów w logach po biegu (`adb logcat | grep -i crash\|ANR`)

#### Ręczne

- [x] 2.2 TTS słyszalny w słuchawkach Bluetooth
- [x] 2.3 Screen-off: TTS kontynuuje bez przerwy
- [x] 2.4 Sterowanie z słuchawek (ekran off): PLAY/PAUSE, NEXT, PREV
- [x] 2.5 Notification controls działają; tekst i kolory aktualizują się
- [x] 2.6 30-minutowy bieg bez crashy: TTS gra, wskaźnik aktualny
- [x] 2.7 Pełna pętla kolekcji — powrót do pierwszej fiszki bez przerwy
