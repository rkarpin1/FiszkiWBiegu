# Seedowanie nowego użytkownika z konta-szablonu — Krótki plan

> Pełny plan: `context/changes/seed-new-user/plan.md`

## Co i dlaczego

Przy pierwszej rejestracji nowego użytkownika backend kopiuje wszystkie kolekcje i fiszki z konta-szablonu `rkarpin1@gmail.com`, aby nowy user od razu miał gotowy materiał do nauki zamiast pustego konta.

## Punkt wyjścia

Logowanie (`handlers/auth.rs`) robi upsert użytkownika i nie odróżnia nowego konta od istniejącego. Kolekcje i fiszki tworzone są wstawianiem wąskiego podzbioru kolumn, więc reszta (SRS, statystyki) pochodzi z domyślnych — co czyni „świeży reset" przy kopii naturalnym.

## Pożądany stan końcowy

Po pierwszym zalogowaniu świeżego konta Google jego lista kolekcji zawiera kopie wszystkich kolekcji szablonu (z fiszkami), z nowymi ID i wyzerowanym postępem nauki. Ponowne logowanie nie duplikuje danych. Błąd kopii lub brak szablonu nie blokuje logowania.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
| ------- | ----- | ------------------- | ------ |
| Wyzwalacz | Tylko pierwsza rejestracja (`xmax = 0`) | Deterministyczne, raz na życie konta; brak ponownego zalewania po usunięciu kolekcji | Plan |
| E-mail szablonu | Stała w kodzie | Zero konfiguracji, najprościej dla MVP | Plan |
| Postęp przy kopii | Świeży reset (SRS=0, statystyki 0/null) | Nowy user uczy się od zera, statystyki własne | Plan |
| Obsługa błędów | Best-effort (login zawsze działa) | Krytyczna ścieżka logowania nie pada przez seedowanie | Plan |
| Atomowość kopii | Jedna transakcja (wszystko-albo-nic) | Brak częściowych/niespójnych kont | Plan |
| Testy | Tylko ręczne | Pełny przepływ wymaga realnego Google; istniejący pakiet ma nie regresować | Plan |

## Zakres

**W zakresie:**
- Nowy moduł `seed.rs` z transakcyjną kopią kolekcji + fiszek z konta-szablonu
- Wykrycie nowego użytkownika w `login` + best-effort wywołanie seedowania

**Poza zakresem:**
- Zmiany schematu / migracje, zmiany frontendu
- Konfiguracja przez env, re-seedowanie istniejących/pustych kont
- Nowe testy automatyczne, kopiowanie historii nauki szablonu

## Architektura / Podejście

`login` rozszerza `RETURNING` upsertu o `(xmax = 0) AS is_new`; gdy `is_new`, wywołuje `seed_new_user(pool, user.id)` best-effort. `seed_new_user` ustala konto-szablon po e-mailu (stała), a następnie w jednej transakcji: dla każdej kolekcji szablonu wstawia nową kolekcję dla nowego usera, pobiera jej ID i kopiuje fiszki przez `INSERT ... SELECT` (reset SRS/statystyk z domyślnych). Podzbiory kolumn lustrzane do istniejących handlerów `create`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
| ---- | ------------ | --------------- |
| 1. Moduł seedowania | `seed_new_user` (transakcyjna kopia + reset) | Poprawne mapowanie stare→nowe ID kolekcji |
| 2. Wpięcie w login | Wykrycie `is_new` (`xmax=0`) + best-effort wywołanie | Niezawodne odróżnienie insert vs update w upsercie |

**Wymagania wstępne:** konto `rkarpin1@gmail.com` istnieje i ma kolekcje/fiszki.
**Szacowany nakład pracy:** ~1 sesja, 2 fazy, 3 pliki (`seed.rs`, `lib.rs`, `handlers/auth.rs`).

## Otwarte ryzyka i założenia

- `xmax = 0` wiarygodnie odróżnia insert od update w tym upsercie (przyjęte; standardowy wzorzec PostgreSQL).
- Konto-szablon ma rozsądną liczbę kolekcji/fiszek (kopia synchroniczna w trakcie logowania).
- Brak testu automatycznego oznacza brak ochrony regresji nowej logiki — świadomy wybór.

## Kryteria sukcesu (podsumowanie)

- Nowy user po pierwszym loginie ma kopie kolekcji i fiszek szablonu z resetem SRS i nowymi ID.
- Ponowne logowanie nie duplikuje danych; istniejący użytkownicy bez zmian.
- Błąd kopii / brak szablonu nie blokuje logowania.
