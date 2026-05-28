# Wybór języka kolekcji — Krótki plan

> Pełny plan: `context/changes/collection-language-select/plan.md`

## Co i dlaczego

Dodanie wyboru pary językowej (język ojczysty → język do nauki) przy tworzeniu i edycji kolekcji. Funkcja była zaplanowana w projekcie graficznym `.tmp/UI/_TODO.kt`, ale pominięta w S-04-C z powodu braku pola `language` w modelu. Wymaga zmian zarówno w backendzie (migracja DB + Rust), jak i frontendzie (nowy komponent UI + aktualizacja modeli + nawigacja).

## Punkt wyjścia

Tabela `collections` ma tylko `id, user_id, name, created_at`. `CollectionFormScreen` pokazuje pola NAZWA i OPIS, ale brak selektora języka. `FlashcardsScreen` hardcoduje `"PL → EN"` i flagi `pl`/`en`. `Flag.kt` ma już mapę 6 języków (`LanguageNames`) i gotowe flagi SVG.

## Pożądany stan końcowy

Po zmianie użytkownik wybiera parę językową w formularzu kolekcji (chip z flagą + bottom-sheet). `FlashcardsScreen` wyświetla rzeczywiste flagi i nazwy języków kolekcji zamiast hardcoded PL→EN. Istniejące kolekcje automatycznie dostają domyślne `pl → en`.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego |
|---------|-------|----------|
| Typ DB | TEXT NOT NULL z DEFAULT | Prostsze niż ENUM — dodanie języka bez ALTER TYPE |
| Domyślne języki | pl → en | Spójne z obecnym hardcodem; istniejące kolekcje bez zmian wizualnych |
| Zestaw języków | 6 z Flag.kt (pl,en,de,es,fr,it) | Każdy ma flagę SVG i polską nazwę — gotowe zasoby |
| Walidacja source==target | Blokuj CTA (frontend) + 422 (backend) | Kolekcja pl→pl nie ma sensu |
| UX selektora | Chip + ModalBottomSheet | Zgodny z `.tmp/UI/_TODO.kt` i istniejącym wzorcem w CollectionFormScreen |
| FlashcardsScreen | Dynamiczne flagi z kolekcji | Kolekcja de→fr inaczej pokazywałaby PL→EN |
| Domyślny nowy formularz | pl → en pre-wybrane | Większość użytkowników to Polacy uczący się angielskiego |

## Zakres

**W zakresie:**
- Migracja `collections` + model Rust + handlery (walidacja języka)
- `CollectionDto` i `CollectionRequest` w KMP (serializacja snake_case ↔ camelCase)
- Nowy komponent `LangSelect` (chip + ModalBottomSheet)
- `CollectionFormScreen` — 2 nowe pola LangSelect + walidacja
- `FlashcardsScreen` — dynamiczne flagi i napisy
- `CollectionsScreen` — zmiana `onEditClick` na `(CollectionDto)` z językami
- `App.kt` — `Route.CollectionForm` + parametry języka

**Poza zakresem:**
- Zmiana tabeli `flashcards` (kolumny `polish_text`/`english_text` zostają)
- Wyświetlanie pary językowej w LaneRow na liście kolekcji
- `TranslateService` (nadal disabled)
- Nowe języki poza 6 obsługiwanymi przez Flag.kt

## Architektura / Podejście

Pionowy stos: backend (migracja → Rust model → handlery) → frontend data layer (Kotlin model → repo → VM) → frontend UI (LangSelect komponent → CollectionFormScreen → FlashcardsScreen → nawigacja). Każda faza daje się samodzielnie skompilować i przetestować. `LangSelect` reużywa `LanguageNames` z `Flag.kt` i `ModalBottomSheet` z Material3 (już importowany).

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|----------------|
| 1. Backend | Migracja DB + walidacja języków w API | Migracja na prod Supabase wymaga dostępu do DB |
| 2. Frontend data layer | CollectionDto/Request + repo + VM z językami | Kompilacja — @SerialName musi zgadzać się z API |
| 3. Frontend UI | LangSelect + formularze + FlashcardsScreen | ModalBottomSheet w LangSelect wymaga `@OptIn` |

**Wymagania wstępne:** S-04-C zaimplementowane (CollectionFormScreen już istnieje)
**Szacowany nakład:** ~2-3 sesje w 3 fazach

## Otwarte ryzyka i założenia

- Deployment backendu na Render.com wymaga ręcznego `cargo run` z migracją — plan zakłada że `sqlx` uruchamia migracje przy starcie
- `LanguageNames` jest `val` na poziomie pakietu w `Flag.kt` — importowalny z `LangSelect.kt` (Kotlin widoczność pakietu)

## Kryteria sukcesu (podsumowanie)

- Tworzenie kolekcji z parą DE→FR działa end-to-end (zapis + wyświetlanie w FlashcardsScreen)
- Istniejące kolekcje wyświetlają PL→EN bez zmian
- CTA disabled gdy oba selektory wskazują ten sam język
