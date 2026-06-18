Zainkrementuj wersję patch w `apps/backend/Cargo.toml` (np. `0.1.9` → `0.1.10`), a następnie wykonaj kolejno:

1. Odczytaj aktualną wersję z `apps/backend/Cargo.toml` (linia `version = "X.Y.Z"` w sekcji `[package]`).

2. Oblicz nową wersję: inkrementuj ostatni segment (patch). Zaktualizuj plik za pomocą narzędzia Edit.

3. Uruchom `cargo sqlx prepare` w katalogu `apps/backend/` (aktualizuje cache zapytań `.sqlx/`). Jeśli zakończy się błędem, wypisz komunikat i zatrzymaj się.

4. Uruchom `cargo build --release` w katalogu `apps/backend/`. Jeśli zakończy się błędem, wypisz komunikat i zatrzymaj się.

Po zakończeniu wypisz: starą wersję, nową wersję i potwierdzenie że oba polecenia przeszły.
