# S-02: Kompletna sesja nauki audio offline — Krótki plan

> Pełny plan: `context/changes/s-02/plan.md`

## Co i dlaczego

Weryfikacja E2E, że użytkownik może przeprowadzić pełną sesję nauki audio podczas biegu — TTS, Foreground Service, sterowanie słuchawkami, praca z ekranem wyłączonym przez 30+ minut. Rdzeń jest już zaimplementowany; ta zmiana potwierdza działanie w warunkach rzeczywistych i naprawia błędy odkryte podczas testów.

## Punkt wyjścia

Wszystkie wymagania FR-010–FR-014 mają implementację w kodzie: TTS loop (PL + 3×EN), MediaSessionService, screen-off via Foreground Service, headphone routing via Media3, pętla powrotu (modulo). F-01 domknęło NFR offline. Brak gwarancji, że całość działa bez crashy przez 30+ minut w warunkach biegu.

## Pożądany stan końcowy

Rafał może uruchomić aplikację, wybrać kolekcję, nacisnąć „▶ Nauka", włożyć telefon do kieszeni i słuchać fiszek przez cały bieg — sterując przyciskami słuchawek Bluetooth, bez internetu, bez crashy.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---|---|---|---|
| „Ostatnia kolekcja" | Poza zakresem | Nie blokuje core NFR; osobna zmiana jeśli potrzebna | Plan |
| Audio focus | Poza zakresem MVP | PRD FR-010–FR-014 nie wymaga; Android system częściowo to obsługuje | Plan |
| Bug handling | Fix w tej samej zmianie | S-02 zamykamy gdy wszystko przechodzi, nie wcześniej | Plan |
| Testy jednostkowe | Brak | TTS/Service wymagają real Android — mockowanie nie weryfikuje NFR | Plan |

## Zakres

**W zakresie:**
- Build + install APK na urządzeniu testowym
- Smoke test wszystkich scenariuszy E2E (pauza, nawigacja, pętla, drugi start, offline guard)
- Full-run test: 30+ min, słuchawki BT, ekran off
- Code fixy wszelkich bugów odkrytych podczas testów

**Poza zakresem:**
- "Ostatnia używana kolekcja" — brak persystencji
- Audio focus (duck/pause przy połączeniu, innej aplikacji)
- Zmiana hardkodowanych czasów pauz (chyba że empirycznie złe)
- Nowe UI w LearningScreen

## Architektura / Podejście

Zmiana czysto weryfikacyjna. Przechodzimy przez dwie warstwy testów: Faza 1 = scenariusze kontrolowane (urządzenie podłączone, ADB), Faza 2 = warunki biegu (BT headphones, screen off, 30 min). Każdy odkryty bug jest naprawiany i commitowany przed kolejnym testem; zmiana zamykana gdy obie fazy przechodzą.

```
Faza 1 (smoke)    →    Fix jeśli coś nie gra    →    Faza 2 (full run)
  Emulator/device         commit per fix             BT headphones, 30 min
  ADB logs                                           real conditions
```

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|---|---|---|
| 1. Smoke test | Wszystkie scenariusze E2E w kontrolowanym środowisku | TTS init race, second-entry bug |
| 2. Full-run test | 30+ min stabilność: wakelock, BT, screen-off | Crash w tle nie do naprawienia bez logów |

**Wymagania wstępne:** Urządzenie Android z Bluetooth, słuchawki BT, kolekcja z fiszkami w bazie  
**Szacowany nakład pracy:** 1–2 sesje; głównie czas testowania + ewentualne bugfixy

## Otwarte ryzyka i założenia

- TTS initialization bez timeoutu (`while (!ttsReady) delay(100)`) — może wisieć jeśli TTS init zawiedzie; do proaktywnego sprawdzenia w Fazie 1
- MediaController `buildAsync()` — headphone commands mogą być gubione w pierwszych ~ms po starcie sesji
- 30-min wakelock: Android Doze może ograniczyć Foreground Service; nieznane bez prawdziwego testu

## Kryteria sukcesu (podsumowanie)

- TTS odtwarza fiszki przez pełne 30 minut bez przerwy, z telefonem w kieszeni
- Przyciski słuchawek Bluetooth działają przy wyłączonym ekranie
- Po powrocie z biegu: brak crashy w logach ADB
