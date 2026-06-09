Odśwież wszystkie pliki dokumentacyjne projektu w katalogu `context/foundation/`.

## Krok 1 — Odkryj aktualne pliki

Użyj `Glob` na `context/foundation/*` aby uzyskać aktualną listę plików.
Nie zakładaj z góry jakie pliki istnieją — lista może się zmieniać.

## Krok 2 — Sklasyfikuj każdy plik

Dla każdego znalezionego pliku ustal jego typ:

| Typ | Opis | Strategia odświeżenia |
|-----|------|-----------------------|
| **spec** | Kontrakt API (openapi.yaml, swagger) | Porównaj z trasami w `src/routes/` |
| **arch** | Architektura, stack, deployment (tech-stack.md) | Porównaj z konfiguracją repo i kodem |
| **schema** | Schemat bazy, migracje (db_scheme.md) | Porównaj z plikami w `migrations/` |
| **roadmap** | Status zadań (roadmap.md) | Porównaj statusy z faktycznym kodem |
| **rules** | Lekcje, reguły (lessons.md) | Dodaj nowe wzorce z bieżącej sesji |
| **source** | Dokument źródłowy (shape-notes.md, zalozenia-systemu.md, zasady-tworzenia-interfejsu.md, prd.md) | Zazwyczaj bez zmian — sprawdź tylko czy nie ma oczywistych rozbieżności |
| **index** | README, indeksy | Zaktualizuj jeśli zmieniła się struktura |

Jeśli napotkasz nieznany plik — przeczytaj pierwsze 10 linii i przypisz typ.

## Krok 3 — Odśwież każdy plik

Dla plików typu **spec**, **arch**, **schema**, **roadmap**:
1. Przeczytaj aktualną zawartość pliku
2. Porównaj z aktualnym stanem kodu (routes, modele, migracje, konfiguracja deploymentu)
3. Zaktualizuj przestarzałe informacje — nazwy plików, URLe, statusy, opisy architektury

Dla plików typu **rules**:
- Dodaj nowe lekcje jeśli w tej sesji pojawiły się nowe wzorce lub błędy warte zapamiętania

Dla plików typu **source** i **index**:
- Przeczytaj, sprawdź czy coś wymaga korekty, zazwyczaj pozostaw bez zmian

## Krok 4 — Podsumowanie

Po przejrzeniu każdego pliku podaj krótkie podsumowanie w formacie:
`<plik>: <co zmieniono> | <co było aktualne>`

Jeśli jakiś plik nie pasuje do żadnego typu lub wymaga decyzji użytkownika — zatrzymaj się i zapytaj.
