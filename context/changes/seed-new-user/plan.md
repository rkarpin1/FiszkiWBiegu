# Seedowanie nowego użytkownika z konta-szablonu — Plan implementacji

## Przegląd

Przy pierwszej rejestracji (pierwsze utworzenie wiersza w `users`) backend kopiuje wszystkie kolekcje i fiszki z konta-szablonu `rkarpin1@gmail.com` do nowego użytkownika. Kopia jest transakcyjna (wszystko-albo-nic), z resetem postępu nauki (świeży SRS, wyzerowane statystyki), i best-effort względem logowania — błąd kopiowania lub brak szablonu nigdy nie blokuje zalogowania.

## Analiza stanu obecnego

- **Login = upsert** (`handlers/auth.rs:61-72`): `INSERT INTO users (...) ON CONFLICT (google_id) DO UPDATE ... RETURNING ...`. Po walidacji tokenu Google zwraca JWT. **Nie ma rozróżnienia insert vs update** — brak wykrycia „nowego" użytkownika.
- **Schemat kolekcji** (`handlers/collections.rs:50-53`): tworzenie wstawia tylko `(user_id, name, description, source_language, target_language)`; `last_studied`, `total_study_minutes`, `created_at` mają domyślne (null / 0 / now). `progress` nie jest kolumną (wyliczany).
- **Schemat fiszek** (`handlers/flashcards.rs:77-79`): tworzenie wstawia tylko `(collection_id, source_text, target_text, position)`; `srs_level`, `last_studied_at`, `created_at` mają domyślne (reset: 0 / null / now). To potwierdza, że **kopia tych samych podzbiorów kolumn automatycznie daje świeży reset**.
- **Konfiguracja** idzie przez env w `main.rs` → `web::Data`. Pool `PgPool` jest dostępny w handlerach jako `web::Data<PgPool>`.
- **Testy**: `tests/` (testcontainers) mintują JWT bezpośrednio i nie przechodzą przez `login` (realny Google). Wybór: **bez nowego testu integracyjnego**, weryfikacja ręczna.

## Pożądany stan końcowy

Po pierwszym zalogowaniu nowego konta Google jego lista kolekcji zawiera kopie wszystkich kolekcji szablonu (te same nazwy, opisy, języki, fiszki z tym samym tekstem i pozycją), z nowymi identyfikatorami, `srs_level = 0`, `last_studied_at = null`, `total_study_minutes = 0`, `last_studied = null`. Ponowne logowanie istniejącego użytkownika niczego nie kopiuje. Brak szablonu lub błąd kopii → login nadal zwraca token; problem widoczny tylko w logach.

Weryfikacja: kompilacja przechodzi, istniejący pakiet testów przechodzi (brak regresji), a ręczny przepływ rejestracji potwierdza kopię z resetem.

### Kluczowe odkrycia:

- Kopiowanie tych samych podzbiorów kolumn co istniejące handlery `create` daje reset SRS/statystyk z domyślnych wartości tabel (`collections.rs:50-53`, `flashcards.rs:77-79`).
- Fiszki wskazują na `collection_id`, więc kopia wymaga mapowania stare→nowe ID kolekcji — pętla w Rust w jednej transakcji jest najprostsza i czytelna.
- Wykrycie świeżo wstawionego wiersza: systemowa kolumna `(xmax = 0)` w `RETURNING` upsertu (`= true` dla INSERT, `= false` dla ścieżki `DO UPDATE`).

## Czego NIE robimy

- Brak zmian schematu (żadnej migracji).
- Brak zmian frontendu.
- Brak konfiguracji przez env — e-mail szablonu jest stałą w kodzie.
- Brak re-seedowania kont z czasem (tylko przy pierwszej rejestracji; usunięcie kolekcji nie wywołuje ponownego zalania).
- Brak nowego testu integracyjnego (weryfikacja ręczna; istniejący pakiet ma tylko nie regresować).
- Nie kopiujemy historii nauki/statystyk szablonu (świadomy reset).

## Podejście do implementacji

Nowy moduł `seed.rs` z jedną funkcją `seed_new_user(pool, new_user_id)`, wykonującą transakcyjną kopię z konta-szablonu (stała e-mail). Login w `handlers/auth.rs` rozszerza `RETURNING` o `(xmax = 0)` aby wykryć nowego usera i — tylko wtedy — wywołuje `seed_new_user` best-effort (błąd logowany, odpowiedź bez zmian). Dwie fazy: najpierw samodzielny moduł kopiujący, potem wpięcie w ścieżkę logowania.

## Krytyczne szczegóły implementacji

- **Wykrycie insert vs update**: w upsercie z `ON CONFLICT DO UPDATE` świeżo wstawiony wiersz ma `xmax = 0`, a wiersz zaktualizowany ma `xmax <> 0`. To jedyny wiarygodny sposób odróżnienia „nowy user" w jednym zapytaniu; `created_at ≈ now()` jest kruche i go nie używamy.
- **Best-effort + transakcja**: sama kopia jest atomowa (jedna transakcja → rollback przy błędzie, brak częściowych danych), ale jej wywołanie z `login` jest best-effort — `Err` jest tylko logowany (`eprintln!`), a `login` mimo to mintuje JWT. Te dwie własności muszą współgrać: złapany błąd ⇒ czyste konto (rollback) + udany login.

## Faza 1: Moduł seedowania

### Przegląd

Samodzielny moduł kopiujący kolekcje + fiszki z konta-szablonu do wskazanego użytkownika.

### Wymagane zmiany:

#### 1. Nowy moduł kopiujący

**Plik**: `apps/backend/src/seed.rs` (nowy)

**Cel**: Dostarczyć funkcję, która transakcyjnie kopiuje wszystkie kolekcje i fiszki konta-szablonu do nowego użytkownika, z resetem postępu.

**Kontrakt**: `pub async fn seed_new_user(pool: &sqlx::PgPool, new_user_id: uuid::Uuid) -> Result<(), sqlx::Error>`. Stała `const SEED_TEMPLATE_EMAIL: &str = "rkarpin1@gmail.com";`. Zachowanie: ustal `template_id` przez `SELECT id FROM users WHERE email = SEED_TEMPLATE_EMAIL` (`fetch_optional`); jeśli brak **lub** `template_id == new_user_id`, zwróć `Ok(())` (no-op). W przeciwnym razie w jednej transakcji (`pool.begin()`): dla każdej kolekcji szablonu (`SELECT id, name, description, source_language, target_language FROM collections WHERE user_id = template_id`) wstaw nową kolekcję dla `new_user_id` kopiując `name/description/source_language/target_language` (pozostałe pola z domyślnych → reset), pobierz `RETURNING id`, a następnie skopiuj jej fiszki przez `INSERT INTO flashcards (collection_id, source_text, target_text, position) SELECT <new_id>, source_text, target_text, position FROM flashcards WHERE collection_id = <old_id>` (kolumny `srs_level`/`last_studied_at` z domyślnych → reset). Na końcu `tx.commit()`. Podzbiory kolumn lustrzane do `collections.rs:50-53` i `flashcards.rs:77-79`.

#### 2. Rejestracja modułu w crate

**Plik**: `apps/backend/src/lib.rs`

**Cel**: Udostępnić moduł `seed` reszcie crate'a (wywołanie z `handlers::auth`).

**Kontrakt**: Dodać deklarację `mod seed;` (lub `pub mod seed;`) obok istniejących `pub mod ...` (`lib.rs:4-8`). Funkcja pozostaje nieużywana do Fazy 2 — dopuszczalne ostrzeżenie `dead_code` (brak `deny(warnings)` w projekcie).

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Kompilacja przechodzi: `cargo check --tests` (z `apps/backend`)
- [ ] Istniejący pakiet testów przechodzi (brak regresji): `cargo test` (z `apps/backend`; wymaga Dockera)

#### Weryfikacja ręczna:

- [ ] Przegląd logiki kopii: podzbiory kolumn lustrzane do handlerów `create`, reset SRS/statystyk wynika z domyślnych, kopia objęta jedną transakcją

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 2.

---

## Faza 2: Wpięcie w login

### Przegląd

Wykrycie świeżo utworzonego użytkownika w `login` i best-effort wywołanie seedowania.

### Wymagane zmiany:

#### 1. Wykrycie nowego usera + wywołanie seedowania

**Plik**: `apps/backend/src/handlers/auth.rs`

**Cel**: Rozpoznać, że upsert utworzył nowy wiersz, i tylko wtedy zaseedować konto — bez wpływu na odpowiedź logowania.

**Kontrakt**: Rozszerzyć `RETURNING` upsertu (`auth.rs:61-72`) o `(xmax = 0) AS is_new` i odczytać tę flagę razem z `User` (np. lokalna struktura `#[derive(FromRow)]` spłaszczająca pola `User` + `is_new: bool`, albo odczyt przez `sqlx::query` + ręczne mapowanie). Po udanym upsercie: jeśli `is_new == true`, wywołać `crate::seed::seed_new_user(pool.get_ref(), user.id).await` i przy `Err(e)` jedynie `eprintln!("seed error: {e}")` (best-effort — nigdy nie zmieniaj odpowiedzi). Dalej mintowanie JWT bez zmian. Dla istniejącego użytkownika (`is_new == false`) zachowanie bez zmian.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Kompilacja przechodzi: `cargo check --tests` (z `apps/backend`)
- [ ] Istniejący pakiet testów przechodzi (brak regresji): `cargo test` (z `apps/backend`; wymaga Dockera)

#### Weryfikacja ręczna:

- [ ] Rejestracja nowego konta Google → nowy user ma kopie kolekcji i fiszek szablonu, z nowymi ID, `srs_level = 0`, `last_studied_at = null`, `total_study_minutes = 0`, `last_studied = null`
- [ ] Ponowne logowanie istniejącego użytkownika → brak duplikatów (kopia nie wykonuje się ponownie)
- [ ] Scenariusz braku szablonu (np. tymczasowo zmieniony e-mail stałej na nieistniejący) → login nadal zwraca token (best-effort)

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie.

---

## Strategia testowania

### Testy jednostkowe / integracyjne:

- Brak nowych testów automatycznych (decyzja: weryfikacja ręczna). Istniejący pakiet `cargo test` musi nadal przechodzić jako kontrola regresji ścieżki logowania.

### Kroki testowania ręcznego:

1. Upewnij się, że konto `rkarpin1@gmail.com` ma kilka kolekcji z fiszkami.
2. Zaloguj się świeżym kontem Google (nigdy wcześniej nieużytym) → sprawdź na liście kolekcji, że pojawiły się kopie z fiszkami; otwórz kolekcję i potwierdź reset POSTĘP/CZAS (SRS od zera).
3. Wyloguj i zaloguj ponownie tym samym nowym kontem → potwierdź brak duplikatów.
4. (Opcjonalnie) Tymczasowo zmień stałą e-mail na nieistniejący adres, zarejestruj kolejne nowe konto → login działa, konto puste, błąd/no-op w logach.

## Uwagi dotyczące wydajności

Liczba kolekcji/fiszek szablonu jest niewielka; pętla po kolekcjach z `INSERT ... SELECT` dla fiszek wykonuje O(liczba kolekcji) zapytań w jednej transakcji — bez znaczenia dla wydajności. Seedowanie wykonuje się raz na życie konta.

## Uwagi dotyczące migracji

Brak — żadnych zmian schematu. Istniejące konta nie są seedowane wstecznie (tylko nowe rejestracje).

## Referencje

- Upsert logowania: `apps/backend/src/handlers/auth.rs:61-72`
- Wzorzec tworzenia kolekcji (podzbiór kolumn): `apps/backend/src/handlers/collections.rs:49-53`
- Wzorzec tworzenia fiszki (podzbiór kolumn, reset): `apps/backend/src/handlers/flashcards.rs:76-79`
- Rejestracja modułów crate: `apps/backend/src/lib.rs:4-8`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku. Nie zmieniaj nazw tytułów kroków. Zobacz `references/progress-format.md`.

### Faza 1: Moduł seedowania

#### Automatyczne

- [x] 1.1 Kompilacja przechodzi: `cargo check --tests`
- [x] 1.2 Istniejący pakiet testów przechodzi: `cargo test`

#### Ręczne

- [x] 1.3 Przegląd logiki kopii (podzbiory kolumn, reset z domyślnych, jedna transakcja)

### Faza 2: Wpięcie w login

#### Automatyczne

- [ ] 2.1 Kompilacja przechodzi: `cargo check --tests`
- [ ] 2.2 Istniejący pakiet testów przechodzi: `cargo test`

#### Ręczne

- [ ] 2.3 Rejestracja nowego konta → kopie kolekcji i fiszek z resetem i nowymi ID
- [ ] 2.4 Ponowne logowanie istniejącego użytkownika → brak duplikatów
- [ ] 2.5 Brak szablonu → login nadal zwraca token (best-effort)
