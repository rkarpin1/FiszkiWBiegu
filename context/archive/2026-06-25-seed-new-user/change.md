---
change_id: seed-new-user
created: 2026-06-25
updated: 2026-06-25
status: archived
archived_at: 2026-06-25T09:53:27Z
---

# seed-new-user

Przy pierwszej rejestracji nowego użytkownika skopiować wszystkie kolekcje i fiszki z konta-szablonu `rkarpin1@gmail.com` do nowego użytkownika.

## Goal

Nowy użytkownik po pierwszym zalogowaniu ma od razu komplet kolekcji i fiszek skopiowany z konta-szablonu (`rkarpin1@gmail.com`), z wyzerowanym postępem nauki (świeży SRS), bez wpływu na niezawodność logowania.

## Context

Logowanie (`handlers/auth.rs`) robi upsert użytkownika i nie wykrywa dziś „nowego" konta. Plan dodaje wykrycie świeżo utworzonego usera (`(xmax = 0)` w `RETURNING`) i transakcyjną, best-effort kopię kolekcji + fiszek z konta-szablonu (stała e-mail w kodzie), z resetem `srs_level`/`last_studied_at` i statystyk kolekcji. Brak zmian schematu i frontendu. Weryfikacja ręczna (bez nowego testu integracyjnego).
