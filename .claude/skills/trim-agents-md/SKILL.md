---
name: trim-agents-md
description: >
  Minimalizuje zawartość plików AGENTS.md: usuwa wiedzę powszechną, zastępuje
  duplikaty linkami do kanonicznych dokumentów, zachowuje tylko informacje
  unikalne i nieoczywiste dla profesjonalnego programisty. Produkuje tabelę
  przeglądową do akceptacji użytkownika, a następnie stosuje zatwierdzone zmiany.
  Użyj gdy: invoking /trim-agents-md, użytkownik mówi "skróć AGENTS.md",
  "wyczyść reguły AI", "usuń nadmiar z AGENTS", "zmniejsz AGENTS.md".
allowed-tools:
  - Read
  - Glob
  - Grep
  - Edit
  - Write
  - AskUserQuestion
  - Skill
---

# Trim AGENTS.md

Skraca plik(i) AGENTS.md do esencji — informacji unikalnych i nieoczywistych
dla profesjonalnego programisty. Duplikaty są zastępowane linkami `@`.
Wiedza trywialna jest usuwana. Sprzeczności z innymi plikami są naprawiane.

**Cel:** każda linia w AGENTS.md powinna odpowiadać na pytanie
„Czy wiedziałem to bez tego pliku?" odpowiedzią „NIE".

## Co zawsze ZOSTAW (nigdy nie usuwaj)

1. **Pułapki i obejścia** — zachowania, które AI zaimplementowałoby inaczej bez tej reguły; projekt celowo odbiega od domyślnego podejścia frameworka
2. **Konwencje UI specyficzne dla projektu** — wzorce layoutu nieoczywiste przy lekturze komponentu (np. wymagana struktura dual-layout, konwencje spacing, kolejność sekcji kart)
3. **Reguły bezpieczeństwa** — CORS, autoryzacja, walidacja na granicy systemu
4. **Niestandardowe konwencje** — ustawienia lub wzorce aktywnie sprzeczne z tym, co framework robi out-of-the-box
5. **Tokeny i struktury JWT** — format claims, gdzie jest przechowywany
6. **Reguły RLS i migracji** — nieoczywiste wymagania bazy danych specyficzne dla projektu
7. **Reguły RBAC per endpoint** — które role mają dostęp do czego

## Czego skill NIE robi

- Nie zmienia żadnych innych plików poza AGENTS.md.
- Nie stosuje żadnych zmian bez akceptacji użytkownika.
- Nie skraca informacji unikalnych i nieoczywistych — nawet jeśli są długie.

## Wejście

`$ARGUMENTS` — opcjonalna ścieżka do pliku lub katalogu. Jeśli brak, skanuj
`**/AGENTS.md` w bieżącym repozytorium.

## Procedura

### Krok 0 — Przegląd strukturalny (10x-rule-review)

Dla każdego docelowego pliku AGENTS.md wywołaj skill `10x-rule-review` z jego ścieżką jako argumentem:

```
Skill("10x-rule-review", "<ścieżka do pliku>")
```

Zachowaj wyniki jako kontekst dla Kroku 2. Szczególnie istotne dla trima są:
- **Sprawdzenie 3 (Precyzyjny język)** — niejasne frazy do przepisania lub usunięcia
- **Sprawdzenie 4 (Nadmiarowa wiedza)** — nadmiarowe sekcje pokrywają się z kryteriami trima Q1/Q2
- **Sprawdzenie 2 (Bezpośrednie fragmenty)** — osadzone bloki kodu do zastąpienia `@`-linkami

Sprawdzenie 5 (kolejność) jest czysto informacyjne. Sprawdzenie 1 (długość): wynik WARN/FAIL oznacza bardziej agresywny trim — przy wątpliwościach kwalifikuj pozycje o ważności `niska`/`średnia` do `USUŃ` zamiast `ZOSTAW`. Uwzględnij oba w tabeli przeglądowej.

Jeśli `10x-rule-review` jest niedostępny, pomiń ten krok i kontynuuj od Kroku 1.

### Krok 1 — Zbierz kontekst

Przeczytaj:
1. Docelowy plik(i) AGENTS.md.
2. `CLAUDE.md` i dowolne `@`-referenced pliki (foundation, lessons, openapi, tech-stack).
3. Kanoniczne źródło endpointów (np. `openapi.yaml`, `swagger.json` — znajdź w projekcie).
4. Kanoniczne źródło architektury i auth flow (np. `tech-stack.md`, `README.md`).
5. Plik lekcji/reguł projektu (np. `lessons.md`, `CLAUDE.md`).

**Dokumenty kanoniczne tego projektu** — użyj `Glob` aby znaleźć wszystkie pliki w `context/foundation/`, następnie przeczytaj każdy z nich (lub przynajmniej pierwsze 20 linii, jeśli plik jest duży) aby zrozumieć jego zakres. Na tej podstawie zbuduj mapę: "kategoria informacji → plik kanoniczny". Nie zakładaj z góry jakie pliki istnieją — katalog może się zmieniać.

Orientacyjne kategorie do zmapowania:
- Endpointy API → plik spec (openapi, swagger, itp.)
- Architektura, auth flow, deployment → plik tech-stack lub README
- Role użytkowników, wymagania → PRD lub plik wymagań
- Schemat bazy, migracje → plik schematu DB
- Pułapki i zarejestrowane reguły → plik lessons/lekcji
- Reguły UI/UX, wzorce interfejsu → plik zasad UI

### Krok 2 — Oceń każdą sekcję

Dla każdego akapitu/sekcji/linii AGENTS.md zadaj trzy pytania.
**Przed oceną sprawdź listę "Co zawsze ZOSTAW" powyżej — te kategorie są immutable.**

> Jeśli Krok 0 wykonał `10x-rule-review`, sekcje oznaczone tam jako NADMIAROWE lub NIEJASNE traktuj jako wstępnie zakwalifikowane do `USUŃ` / `NAPRAW` — weryfikuj tylko, czy nie wpadają w kategorię "zawsze ZOSTAW".

**Q1 — Powszechna wiedza?**
Czy profesjonalny programista znający stos projektu wiedziałby to bez czytania pliku?
Jeśli tak → `USUŃ`.

Typowe przykłady wiedzy powszechnej (zawsze weryfikuj w kontekście stosu projektu):
- Standardowe polecenia frameworka/narzędzia (build, test, lint, format)
- Standardowe derive/dekoratory wymagane przez biblioteki ORM/serialization
- Domyślna lokalizacja komponentów UI w scaffoldowanym projekcie
- Standardowe aliasy ścieżek konfigurowane przez CLI frameworka

**Q2 — Duplikat innego dokumentu?**
Czy ta informacja istnieje już w kanonicznych dokumentach projektu?
Jeśli tak → `ZASTĄP @link`. Format zastąpienia: patrz `## Wzorzec zastąpień` na końcu pliku.

Wzorzec mapowania (dostosuj do struktury projektu):
- Tabele endpointów → kanoniczny plik API spec (OpenAPI, Swagger, itp.)
- Auth flow (kroki OAuth, JWT storage) → plik architektury/tech-stack
- Role użytkowników (definicje) → PRD lub plik wymagań
- Reguły deploymentu/CI → plik konfiguracji lub tech-stack
- Twarde reguły projektowe → CLAUDE.md / główny plik reguł

**Q3 — Sprzeczność?**
Czy wpis jest sprzeczny z plikiem lekcji lub głównym plikiem reguł projektu?
Jeśli tak → `NAPRAW` (dostosuj do obowiązującej reguły) lub `USUŃ`.

### Krok 3 — Zbuduj tabelę przeglądową

Wyprodukuj tabelę dla każdego pliku AGENTS.md z kolumnami:

| Sekcja / linia | Treść (skrót) | Akcja | Powód | Ważność dla AI | Ważność dla Ciebie |
|---|---|---|---|---|---|

Akcje: `ZOSTAW` / `USUŃ` / `ZASTĄP @link` / `SKRÓĆ` / `NAPRAW`

Ważność: `krytyczna` / `wysoka` / `średnia` / `niska` / `-`

**Zasada oceny ważności:**
- `krytyczna` — reguła zapobiega awarii, utracie danych lub bezpieczeństwa
- `wysoka` — unikalna wiedza projektowa, bez której AI popełni błąd
- `średnia` — pomocna, ale AI dojdzie do tego czytając kod
- `niska` — convenience, można pominąć bez szkody
- `-` — trywialna/duplikat, usuń

### Krok 4 — Zapytaj użytkownika

Użyj `AskUserQuestion` z pytaniem o podejście:
- **Zastosuj wszystkie sugestie** — usuń/zastąp/skróć zgodnie z tabelą
- **Zastosuj tylko USUŃ i ZASTĄP** — bez skracania
- **Pokaż najpierw diff** — wygeneruj podgląd bez zapisu
- **Wybiorę ręcznie** — omów konkretne wpisy

### Krok 5 — Zastosuj zmiany

Tylko po akceptacji użytkownika. Stosuj zmiany atomowo per plik.
Zachowaj strukturę nagłówków (H2/H3). Nie zmieniaj żadnych innych plików.

Po zastosowaniu pokaż skróconą statystykę:
`<plik>: <linie przed> → <linie po> (−<delta> linii, −<procent>%)`

---

## Wzorzec zastąpień

Przy zastępowaniu sekcji linkiem używaj formatu:

```markdown
> Szczegóły: @context/foundation/openapi.yaml
```

lub (dla sekcji z minimalnym kontekstem):

```markdown
### Auth endpoints
> @context/foundation/openapi.yaml — sekcja paths
```

Nigdy nie usuwaj nagłówka sekcji całkowicie jeśli inne pliki go referencjonują.
Zastąp treść linkiem, zachowaj nagłówek.
