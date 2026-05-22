---
project: "FiszkiWBiegu"
version: 1
status: draft
created: 2026-05-22
context_type: greenfield
product_type: mobile
target_scale:
  users: medium
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 4
  hard_deadline: null
  after_hours_only: true
---

## Vision & Problem Statement

Osoby regularnie uprawiające aktywność fizyczną (bieganie, jazda na rowerze, turystyka piesza) tracą dziesiątki godzin miesięcznie, które mogłyby poświęcić na powtarzanie słownictwa języka obcego. Istniejące aplikacje fiszek — Anki, Quizlet, Fiszki.pl — wymagają wzroku i rąk: użytkownik musi patrzeć na ekran i dotykać go, aby przewinąć fiszkę lub ocenić swoją odpowiedź. Podczas biegu smartfon siedzi w kieszeni, ręce pracują, wzrok skierowany jest na trasę. Wynik: albo użytkownik zatrzymuje się, żeby się uczyć, albo nie uczy się wcale.

Wgląd, który sprawia, że ten produkt ma sens: cykl nauki fiszka → przerwa → odpowiedź można w pełni odwzorować w audio. Użytkownik słyszy słowo po polsku, mówi odpowiedź po angielsku w myślach lub głośno, a po chwili słyszy poprawne tłumaczenie. Żadna istniejąca aplikacja na Android nie oferuje tego przepływu z obsługą przycisków słuchawek (PLAY/PAUSE/NEXT/PREV), działając w tle, gdy ekran jest wyłączony.

## User & Persona

### Główna persona (MVP)

**Rafał** — developer i użytkownik w jednym. Regularnie biega, ma słuchawki Bluetooth z przyciskami mediów. Zna angielski na poziomie B2, ale chce rozbudowywać słownictwo domenowe. Uczy się przez fiszki w wolnych chwilach, ale podczas biegania (45-60 min, 4x w tygodniu) słucha muzyki albo nic — czas nauki jest zerowy. Chce mieć aplikację, którą może włączyć przed biegiem i zapomnieć o ekranie.

### Wtórne persony (poza MVP)

- Biegacze / rowerzyści bez background'u technicznego — ta sama potrzeba, nie budują sami
- Turyści — wolniejsze tempo, krótsze sesje

## Success Criteria

### Primary

- Użytkownik może zalogować się, stworzyć kolekcję z fiszkami (ręcznie), włączyć tryb nauki i słyszeć audio fiszek podczas biegu z telefonem w kieszeni i ekranem wyłączonym.
- Przyciski NEXT/PREV na słuchawkach Bluetooth przełączają fiszki w trybie nauki.
- Użytkownik może tworzyć, edytować i usuwać kolekcje oraz fiszki.

### Secondary

- Aplikacja zapamiętuje ostatnio używaną kolekcję i proponuje ją przy następnym uruchomieniu trybu nauki.

### Guardrails

- Audio odtwarzane w tle nie może się zawieszać ani urywać podczas aktywności fizycznej.
- Aplikacja nie może się crashować w tle (telefon w kieszeni, niemożliwy restart).
- Każde usunięcie fiszki lub kolekcji wymaga potwierdzenia przez użytkownika — brak cofania przypadkowych usunięć.

## User Stories

### US-01: Użytkownik uruchamia sesję nauki przed biegiem

- **Given** zalogowany użytkownik z co najmniej jedną kolekcją zawierającą fiszki
- **When** wybiera kolekcję i klika "Rozpocznij naukę"
- **Then** aplikacja uruchamia tryb nauki w tle; użytkownik słyszy tekst po polsku, krótką przerwę, a następnie 3x tekst po angielsku; po cyklu automatycznie przechodzi do kolejnej fiszki

#### Acceptance Criteria

- Audio jest odtwarzane nawet gdy ekran telefonu jest wyłączony
- Naciśnięcie NEXT na słuchawkach przechodzi do następnej fiszki
- Naciśnięcie PREV na słuchawkach wraca do poprzedniej fiszki
- PLAY/PAUSE wstrzymuje i wznawia odtwarzanie
- Pętla kolekcji jest ciągła (po ostatniej fiszce wraca do pierwszej)

### US-02: Użytkownik tworzy kolekcję i dodaje fiszki

- **Given** zalogowany użytkownik
- **When** tworzy kolekcję o nazwie "Słownictwo IT" i dodaje fiszkę "komputer → computer"
- **Then** fiszka pojawia się na liście fiszek w kolekcji i jest gotowa do odtwarzania w trybie nauki

#### Acceptance Criteria

- Fiszka zawiera tekst PL i tekst EN
- Przy próbie usunięcia fiszki lub kolekcji pojawia się dialog potwierdzenia

## Functional Requirements

### Konto i uwierzytelnianie

- FR-001: Użytkownik rejestruje i loguje się przez OAuth Google. Priorytet: must-have
  > Sokrates: Zmieniono z email+hasło na OAuth Google — eliminuje własną obsługę resetowania hasła i walidacji email. Facebook i Apple Sign-In planowane w v2.
- FR-002: Użytkownik może zalogować się przez OAuth Google (ten sam mechanizm co rejestracja). Priorytet: must-have

### Zarządzanie kolekcjami

- FR-003: Użytkownik może tworzyć kolekcje fiszek. Priorytet: must-have
- FR-004: Użytkownik może przeglądać fiszki w kolekcji. Priorytet: must-have
- FR-005: Użytkownik może edytować nazwę kolekcji. Priorytet: must-have
- FR-006: Użytkownik może usunąć kolekcję (z potwierdzeniem). Priorytet: must-have

### Zarządzanie fiszkami

- FR-007: Użytkownik może ręcznie tworzyć fiszki (tekst po polsku + tekst po angielsku) w ramach kolekcji. Priorytet: must-have
- FR-008: Użytkownik może edytować fiszkę. Priorytet: must-have
- FR-009: Użytkownik może usunąć fiszkę (z potwierdzeniem). Priorytet: must-have

### Tryb nauki

- FR-010: Użytkownik może uruchomić tryb nauki dla wybranej kolekcji. Priorytet: must-have
- FR-011: Aplikacja odtwarza audio fiszek w tle za pomocą systemowego TTS Androida (tekst PL, następnie 3x tekst EN z przerwami). Priorytet: must-have
  > Sokrates: Rozważono: "Systemowy TTS może mieć złą jakość wymowy EN." Rozwiązanie: zachowano — Google TTS na Androidzie jest wystarczający dla MVP; chmurowy TTS to poza zakresem MVP.
- FR-012: Tryb nauki działa przy wyłączonym ekranie (telefon w kieszeni). Priorytet: must-have
  > Sokrates: Rozważono: "Android agresywnie zabija procesy w tle." Rozwiązanie: zachowano jako rdzeń funkcjonalności — implementacja wymaga Foreground Service z powiadomieniem systemowym.
- FR-013: Użytkownik może sterować trybem nauki przyciskami słuchawek: PLAY/PAUSE (zatrzymaj/wznów), NEXT (następna fiszka), PREV (poprzednia fiszka). Priorytet: must-have
  > Sokrates: Rozważono: "Przyciski słuchawek mogą być niestandardowe na różnych modelach." Rozwiązanie: zachowano — Media Session API obsługuje standard; NEXT/PREV zależy od producenta słuchawek, ale jest wystarczające dla głównej persony.
- FR-014: Aplikacja zapamiętuje ostatnio używaną kolekcję i proponuje ją przy następnym uruchomieniu trybu nauki. Priorytet: must-have
  > Sokrates: Rozważono: "To nice-to-have zamaskowane jako must-have." Rozwiązanie: zachowano jako must-have — minimalizacja tarcia przed biegiem (jedno tapnięcie START) jest kluczowa dla UX aktywności fizycznej.
- FR-015: Użytkownik może wybrać tryb EN→PL (3x angielski, potem 1x polski). Priorytet: nice-to-have
  > Sokrates: Rozważono: "Czy warto go dodać do MVP?" Rozwiązanie: zachowano jako nice-to-have — PL→EN to główny use case; EN→PL komplikuje UI bez kluczowej wartości dla MVP.

## Non-Functional Requirements

- Odtwarzanie audio jest ciągłe przez całą sesję nauki (30-60 min) bez przerw percepcyjnych >200ms podczas aktywności fizycznej.
- Sesja nauki jest w pełni użyteczna bez aktywnego połączenia z internetem w trakcie biegu — odtwarzanie audio i dostęp do treści fiszek kolekcji nie są uzależnione od łączności sieciowej po uruchomieniu sesji.
- Sterowanie przyciskami słuchawek (NEXT/PREV/PLAY/PAUSE) reaguje w czasie <500ms od naciśnięcia.
- Dane fiszek użytkownika są prywatne: żaden niezalogowany użytkownik ani inny zalogowany użytkownik nie ma dostępu do cudzych kolekcji.

## Business Logic

Aplikacja sekwencyjnie odtwarza audio fiszek według wzorca: tekst w języku źródłowym → pauza proporcjonalna do długości tekstu docelowego (dając użytkownikowi czas na wypowiedzenie odpowiedzi) → N razy tekst w języku docelowym z pauzami między powtórzeniami, w ciągłej pętli przez całą kolekcję.

Dane wejściowe, które reguła konsumuje: para tekstów (PL + EN) na fiszkę, liczba powtórzeń tekstu docelowego (domyślnie 3), kolejność sekwencyjna fiszek w kolekcji. Wyjście: ciąg audio odtwarzany przez syntezę mowy bez ingerencji użytkownika. Użytkownik napotyka regułę pasywnie — jedyną akcją jest naciśnięcie NEXT/PREV na słuchawkach, co przesuwa do innej fiszki bez przerywania wzorca.

Reguła nie podejmuje decyzji o wyborze "następnej fiszki do nauki" (brak algorytmu spaced repetition w MVP) — kolejność jest zawsze sekwencyjna, pętla jest ciągła.

## Access Control

Uwierzytelnianie: OAuth Google. Użytkownik rejestruje konto i loguje się przez Google Sign-In, bez własnego hasła. Facebook i Apple Sign-In planowane w v2. Fiszki i sesje nauki są przechowywane w chmurze powiązanej z kontem Google użytkownika.

Model ról: płaski — jeden typ użytkownika z pełnym dostępem do swoich danych. Brak konta administratora w MVP.

Niezalogowany użytkownik: dostęp tylko do ekranu logowania i rejestracji. Żadne dane fiszek nie są dostępne bez uwierzytelnienia.

Separacja danych: każdy użytkownik widzi wyłącznie swoje kolekcje fiszek, sesje nauki i postępy.

## Non-Goals

- Brak importu fiszek z plików (PDF, CSV, Anki, DOCX) — tylko ręczne tworzenie; import to v2.
- Brak trybu nauki na Web — moduł webApp (Kotlin Multiplatform) służy wyłącznie do zarządzania fiszkami (tworzenie, edycja, przeglądanie); nauka audio działa tylko na Android.
- Brak obsługi iPhone w MVP — projekt używa Kotlin Multiplatform (androidApp + webApp); cel iOS istnieje w szablonie KMP, ale nie jest aktywowany w MVP. Aktywacja iOS planowana w v2.
- Brak systemu oceny znajomości i spaced repetition — brak algorytmu powtórek, brak oceniania UMIEM/NIE UMIEM, brak zapisywania postępów nauki w MVP.
- Brak współdzielenia fiszek między użytkownikami — fiszki są prywatne, brak publicznych kolekcji.
- Brak konta administratora — jeden typ użytkownika w MVP.

## Open Questions

1. **Długość pauz w cyklu audio** — ile sekund po tekście PL (przed EN) i między powtórzeniami EN? Konkretne wartości do ustalenia empirycznie w trakcie implementacji. Blokujące: tak (wpływa na doświadczenie nauki).
2. **Synchronizacja fiszek do pracy offline** — kiedy dokładnie aplikacja synchronizuje fiszki na urządzenie? (przy każdym uruchomieniu? przy starcie trybu nauki? w tle?) Do ustalenia w projekcie architektonicznym. Właściciel: decyzja implementacyjna po wyborze stosu.
3. **Liczba powtórzeń tekstu EN** — domyślnie 3, ale czy powinna być konfigurowalna w MVP? Na MVP hardkodowane 3; konfiguracja planowana w v2.
