# Plan Brief: UI Reskin — Faza C

## Cel w jednym zdaniu

Zastąpić trzy `AlertDialog`-owe formularze (dodaj/edytuj kolekcję, dodaj/edytuj fiszkę) pełnoekranowymi ekranami w stylu Dawn Run i podpiąć je do nawigacji navigation3.

## Decyzje kluczowe

| Decyzja | Wybór | Uzasadnienie |
|---------|-------|--------------|
| VM sharing | `koinViewModel<T>()` zwraca tę samą instancję | Activity ViewModelStore — lista odświeży się po powrocie z formularza |
| Delete z VM | Nowe metody `deleteCollection` / `deleteFlashcard` | Wywołanie `requestDelete` z formularza ustawiłoby flagę i wywołało stary dialog w tle |
| Motyw formularzy | `naturalDark = true` (ciemny) | Spójny z pozostałymi ekranami |
| Pole Opis | Stub widoczny, nie persystowany | Brak `description` w `CollectionDto`; MVP scope |
| Translate button | `Button(enabled=false)`, brak feedbacku | S-04-D; disabled wystarcza |
| Delete z listy | Zostaje | `DeleteConfirmationDialog` zachowany — dwie drogi usunięcia |
| Delete z formularza | ModalBottomSheet (kolekcja) / AlertDialog (fiszka) | PRD hard rule: każda destrukcyjna akcja ma confirm |

## Zmiany plików

| Plik | Akcja |
|------|-------|
| `CollectionFormScreen.kt` | Nowy |
| `CardFormScreen.kt` | Nowy |
| `CollectionsViewModel.kt` | +`updateCollection`, +`deleteCollection` |
| `FlashcardsViewModel.kt` | +`createCard`, +`updateCard`, +`deleteFlashcard` |
| `App.kt` | +Route.CollectionForm, +Route.CardForm, +2 wpisy entryProvider; update entry Collections i Flashcards |
| `CollectionsScreen.kt` | Usuń AddCollectionDialog + EditCollectionDialog; +onAddClick/onEditClick params |
| `FlashcardsScreen.kt` | Usuń FlashcardFormDialog; +onAddCard/onEditCard params |
