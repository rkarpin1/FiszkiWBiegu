## Nazwa aplikacji: FiszkiWBiegu
## Technologia dla frontend: Android, iPhone, Web
## Technologia dla backend: nie ustalona

### Opis i główny cel aplikacji:
Aplikacja FiszkiWBiegu to platforma mobilna, która umożliwia użytkownikom naukę języków obcych poprzez system fiszek.

Aplikacja przeznaczona jest dla osób np. biegaczy, turystów, które chcą w ramach wykonywanej aktywności ( bez użycia rąk) powtórzyć materiał w postaci fiszek.

Aplikacja różni się od innych aplikacji typu "fiski" tym, że materiał w trakcie nauki prezentowany jest wyłącznie w postaci audio.

Aplikacja umożliwia wprowadzanie fiszek, grupowanie fiszek w kategorie, edycję fiszek, usuwanie fiszek, tworzenie sesji nauki, zapisywanie postępów nauki.


### Najmniejszy zestaw funkcjonalności ( MVP )
- tworzenie kolekcji fiszek,
- manualne tworzenie fiszek w ramach kolekcji.
- edycja, usuwanie i prezentacja fiszek w ramach kolekcji
- fiszki zawierają na razie dane do nauki języka angielskiego: czyli tekst po polsku i tekst po angielsku,
- każde usunięcie fiszki, kolekcji musi być potwierdzone przez użytkownika,
- generowanie audio na podstawie tekstu z fiszek z wykorzystaniem systemowego TTS na Android,
- zapisywanie ostatnio użytej kolekcji fiszek do nauki,
- system kont użytkowników, gdzie będą przechowywane fiszki, sesje nauki i postępy w nauce,
- logowanie użytkownika w postaci email / hasło
- tryb nauki, który polega na odtwarzaniu audio wg logiki opisanej w kolejnym punkcie.
- tryb nauki działa w tle aplikacji. Zakładamy, że smartfon trzymany jest w kieszeni.
- tryb nauki działa ze słuchawkami i są obsługiwane PLAY/PAUSE/NEXT/PREVIOUS, gdzie NEXT oznacza kolejną fiszkę w kolekcji, a PREV poprzednią, 
- tryb nauki: polski -> angielski


### Tryb nauki - MVP

W trybie nauki odtwarzany jest jeden kolekcja fiszek w "kółko".
Użytkownik na postawie usłyszanego tekstu po polsku ma powiedzieć tłumaczeniem po angielsku. 
Po chwili użytkownik słyszy tekst po angielsku.

Są dwa tryby:
 - najpierw odtwarzany jest tekst po polsku, a potem 3 razy odtwarzany jest tekst po angielsku z przerwą.
 - odtwarzany jest 3 razy tekst po angielsku z przerwą, a potem 1 raz tekst po polsku.

### Co nie wchodzi w zakres funkcjonalności MVP  ( non goals )
- wprowadzanie fiszek w postaci grafiki, videu, audio,
- importowanie fiszek z plików tekstowych, pdf, docx, 
- aplikacja mobilna na iPhone
- tryb nauki w aplikacji Web
- zapisywanie postępów nauki,
- współdzielenie fiszek między użytkownikami,
- fiszki dostępne dla wszystkich użytkowników,
- konto administrator,
- system oceny znajomości materiału zawarty w fiszce,
- system propozycji fiszek do nauki,


### Funkcjonalność, która jest planowana na dalsze etapy
- aplikacja na iPhone,
- aplikacja na iPhone korzysta z systemowego TTS,
- konto "administrator| do zarządzania globalnymi fiszkami, użytkownikami,
- funkcja oceny znajomości materiału zawarty w fiszce w trybie nauki poprzez wybranie NEXT na słuchawkach interpretowane jest jako "UMIEM", a PREV jako "nie umiem",
- funkcja oceny znajomości materiału po etapie nauki,
- funkcja oceny materiału w trybie nauki wykorzystujący interfejs użytkownika. Czyli użytkownik może korzystać z rąk.
- system powtórek globalnie i w ramach sesji nauki,
- tryb nauki działa wraz z innym odtwarzaczem muzyki,
- wykorzystanie chmurowych systemów do generowania audio z tekstu. W ramach tego zapamiętywanie audio tak, aby nie korzystać zbyt często z w/w systemów
- opcje dotyczące trybu nauki, np. liczba powtórzeń, szybkość odtwarzania audio, tryb losowy / sekwencyjny
- tryb nauki może działać w trybie offline,

### Kryteria sukcesu
- dobre odtwarzanie audio bez przerw czy zacięć,
- aplikacja odtwarza dźwięk w tle, nie zawisa, nie blokuje się,
- działają przyciski PLAY i STOP w słuchawkach,
