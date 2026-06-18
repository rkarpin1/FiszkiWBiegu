---
change_id: srs-learning
title: Algorytm SRS do kolejkowania słów w sesji nauki
status: implementing
created: 2026-06-18
updated: 2026-06-18
archived_at: null
---

## Notes

zaproponuj algorytm do nauku.

Jest lista słów, która jest odtwarzana w kółko.

Gdy wszystko umiemy to każde słowo jest odtwarzane po kolei.

Ocena znajomości słowa ma 3 kategorie:

- nie umiem,

- znam, ale uczę się dalej,

- umiem bardzo dobrze.



Gdy oceniamy słowo na "nie umiem" to musi ono zostać powtórzone jak najszybciej. Najlepiej zaraz za kolejnym słowem.

Gdy oceniamy w/w słowo na "znam" to jest ono odtwarzane później.

Czyli każde słowo ma jakiś zapisany poziom zapamiętania.

np. 0 .0- totalnie nie wiem o co chodzi, do 1.0 - bardzo dobrze znam.



Algorytm ma ta ustawiać listę słów, żeby najważniejsze, - nauczyć słów, które nie umiem, a potem - przypominać, tak aby zostały utrwalone w pamięci.

### Mapowanie ocen na interakcję w aplikacji

- **"nie umiem"** → użytkownik naciska przycisk "nie wiem" — słowo wraca do kolejki jak najszybciej (najlepiej zaraz po następnym słowie)
- **"znam, ale uczę się dalej"** → użytkownik nic nie naciska, słowo jest po prostu odsłuchane — słowo wraca do kolejki później
- **"umiem bardzo dobrze"** → użytkownik naciska przycisk "Wiem!" — słowo odpada z aktywnej kolejki lub jest odtwarzane bardzo rzadko

### Persystencja danych SRS

- Każda fiszka w DB przechowuje dane SRS (poziom znajomości, harmonogram powtórek itp.).
- Po każdym "odegraniu" fiszki w trybie nauki: dane SRS są natychmiast aktualizowane w lokalnej bazie, a następnie asynchronicznie wysyłane na serwer.
- Błędy synchronizacji z serwerem są poza zakresem tego zadania — ignorujemy je (fire & forget).
