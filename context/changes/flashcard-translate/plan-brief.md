# Przycisk „Przetłumacz" w oknie definicji fiszki — Krótki plan

> Pełny plan: `context/changes/flashcard-translate/plan.md`
> Badania: `context/changes/flashcard-translate/research.md`

## Co i dlaczego

Podłączamy istniejący (dziś zablokowany) przycisk „Przetłumacz" w oknie definicji fiszki do darmowego serwisu tłumaczeń, by użytkownik nie musiał ręcznie wpisywać obu stron fiszki. Tłumaczenie idzie przez backend Rust (proxy), bo reguła architektoniczna zabrania trzymania kluczy/sekretów w kliencie.

## Punkt wyjścia

Przycisk „Przetłumacz" jest już w UI jako nieaktywny stub (`CardFormScreen.kt:249-291`), a etykiety pól są zakodowane na sztywno jako „POLSKI"/„ANGIELSKI". Backend nie ma żadnej integracji z API tłumaczeń. Cała ścieżka danych (Ktor client → repo → ViewModel) i wzorce backendu (`reqwest`, `AppError`, walidacja języków) już istnieją do powielenia.

## Pożądany stan końcowy

Użytkownik z tekstem w co najmniej jednym polu klika „Przetłumacz" i dostaje tłumaczenie wpisane do właściwego pola wg reguł kierunku. Pola pokazują rzeczywiste języki kolekcji (nie sztywne pl/en). Błąd API pojawia się jako inline komunikat pod przyciskiem.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
| --- | --- | --- | --- |
| Architektura | Proxy przez backend Rust | Zgodne z regułą „klient tylko przez backend", klucz po stronie serwera | Badania |
| Dostawca | Azure Translator F0 | Najhojniejszy darmowy limit (2 mln zn./mies.), prosta autoryzacja, kody 1:1 | Badania/Plan |
| Organizacja dostawcy | Trait + wybór przez env | Przyszły fallback Azure↔Google to konfiguracja, nie refaktor | Plan |
| Obsługa błędów (UI) | Inline pod przyciskiem | Zawsze widoczny, prosty, bez hosta snackbara | Plan |
| Nadpisywanie Target | Bez pytania | Wprost zgodne z regułą „oba wypełnione → Source→Target" | Plan |
| Etykiety pól | Z języków kolekcji | Naprawa błędu dla kolekcji innych niż pl/en, spójność z kierunkiem | Plan |
| Testy | Tylko ręczne | Decyzja użytkownika; testy budowania pozostają | Plan |

## Zakres

**W zakresie:**
- Endpoint `POST /translate` (trait `TranslationProvider` + impl Azure, env config, walidacja, mapowanie błędów).
- DTO + metoda ApiClient + metoda repozytorium we współdzielonym KMP.
- Akcja ViewModel, aktywacja przycisku z regułami kierunku, inline błąd, naprawa etykiet języków.

**Poza zakresem:**
- Druga implementacja dostawcy (Google/DeepL), self-hosting, bezpośrednie wywołania z klienta.
- Testy automatyczne `/translate`, tłumaczenie wsadowe, auto-detekcja języka, zmiany schematu DB.

## Architektura / Podejście

Ścieżka pionowa: backend (env/`AppState` → trait+Azure → handler chroniony JWT → trasa) → warstwa danych frontendu (DTO → Ktor client → repo) → UI/ViewModel. Reguły kierunku żyją w UI; backend dostaje gotowe `source_language`/`target_language` i waliduje jak kolekcje (`validate_languages`, src≠tgt). Azure wołane przez `reqwest` (domyślny TLS — bez `danger_accept_invalid_certs`).

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
| --- | --- | --- |
| 1. Backend `/translate` | Działający endpoint proxy do Azure | Konfiguracja klucza/regionu Azure; mapowanie błędów API |
| 2. Warstwa danych frontendu | DTO + client + repo we współdzielonym kodzie | Zgodność `@SerialName` z JSON backendu |
| 3. UI i ViewModel | Aktywny przycisk z regułami + etykiety | Poprawne wyznaczenie kierunku i wpisanie wyniku do właściwego pola |

**Wymagania wstępne:** konto Azure z zasobem Translator (F0), `AZURE_TRANSLATOR_KEY` + `AZURE_TRANSLATOR_REGION`.
**Szacowany nakład pracy:** ~2-3 sesje w 3 fazach.

## Otwarte ryzyka i założenia

- Rejestracja konta Azure F0 z PL wymaga karty + weryfikacji telefonu; jeśli niemożliwa — fallback to Google v2 (trait to ułatwia).
- Zakładamy, że kody języków kolekcji (`pl/en/de/es/fr/it`) mapują się 1:1 na kody Azure (małe litery) — potwierdzone w badaniu.

## Kryteria sukcesu (podsumowanie)

- Przycisk tłumaczy poprawnie wg wszystkich czterech reguł kierunku w aplikacji Android.
- Działa dla kolekcji o dowolnej parze języków, z poprawnymi etykietami/flagami.
- Błąd API jest komunikowany użytkownikowi, a formularz pozostaje użyteczny.
