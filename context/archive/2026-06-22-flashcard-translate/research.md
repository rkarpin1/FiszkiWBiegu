---
date: 2026-06-22T00:00:00+02:00
researcher: Robert Karpiński
git_commit: 9285ca18f21da1a34116c96b36f7930679bdb4a9
branch: MVP
repository: FiszkiWBiegu
topic: "Darmowy serwis tłumaczeń dla przycisku „Przetłumacz" w oknie definicji fiszki"
tags: [research, codebase, translation, backend, frontend, card-form]
status: complete
last_updated: 2026-06-22
last_updated_by: Robert Karpiński
---

# Research: Darmowy serwis tłumaczeń dla przycisku „Przetłumacz" w oknie definicji fiszki

**Date**: 2026-06-22T00:00:00+02:00
**Researcher**: Robert Karpiński
**Git Commit**: 9285ca18f21da1a34116c96b36f7930679bdb4a9
**Branch**: MVP
**Repository**: FiszkiWBiegu

## Research Question

Znaleźć darmowy serwis do tłumaczenia z języka na język, dostępny w aplikacji (Android, iPhone, Web). Serwis ma być użyty w oknie definicji fiszki — po naciśnięciu przycisku „Przetłumacz" tekst tłumaczy się wg reguł:

- Source puste, Target wypełnione → tłumacz **Target → Source**
- Source wypełnione, Target puste → tłumacz **Source → Target**
- Oba wypełnione → tłumacz **Source → Target**
- Oba puste → przycisk „Przetłumacz" **wyłączony**

## Decyzje użytkownika (przyjęte przed badaniem)

1. **Architektura**: tłumaczenie wywoływane jako **proxy przez backend Rust** (nie bezpośrednio z klienta).
2. **Typ serwisu**: **darmowy tier z kluczem API** (DeepL / Google / Microsoft).

## Summary

Wybrana architektura jest **zgodna z twardą regułą projektu** (`context/foundation/lessons.md:5-10`): aplikacja komunikuje się wyłącznie z backendem, a klucze/sekrety nie mogą trafiać do klienta. Tłumaczenie należy więc wystawić jako nowy endpoint `POST /translate` w backendzie, który woła zewnętrzne API (przez `reqwest`) z kluczem trzymanym w zmiennej środowiskowej.

**Rekomendowany serwis: Microsoft Azure Translator (tier F0)** — najhojniejszy darmowy limit (2 mln znaków/mies., ~4× DeepL/Google), prosta autoryzacja nagłówkowa idealna do proxy `reqwest`, pełne wsparcie wszystkich 6 języków projektu (pl/en/de/es/fr/it), auto-detekcja języka źródłowego. **Fallback: Google Cloud Translation v2 (Basic)** — najprostsza autoryzacja (klucz w URL). DeepL ma najlepszą jakość, ale schodzi na drugi plan z powodu ryzyka weryfikacji karty przy rejestracji z Polski i najniższego limitu.

Kompatybilność z Androidem/iOS/Web jest **automatyczna** — klient woła własny backend ze współdzielonego kodu KMP (Ktor); nie ma problemu z CORS ani z kluczami w kliencie.

Po stronie frontendu **przycisk „Przetłumacz" już istnieje jako zablokowany stub** z ikoną `Translate` (`CardFormScreen.kt:249-291`) — wystarczy go uaktywnić i podłączyć. Cała ścieżka danych (ApiClient → Repository → ViewModel) ma ustalone wzorce do naśladowania.

## Detailed Findings

### A. Backend Rust — gdzie wpiąć `POST /translate`

Struktura tras: wszystkie endpointy rejestrowane w `register_routes(cfg)` (`apps/backend/src/lib.rs:38-68`), handlery w modułach `apps/backend/src/handlers/` (`mod.rs:1-6`).

**Wzorzec wychodzącego wywołania HTTP (kluczowy)** — istniejąca weryfikacja Google id_token przez `reqwest` w `apps/backend/src/handlers/auth.rs:17-47`: budowa `reqwest::ClientBuilder`, `.send().await`, sprawdzenie `status().is_success()`, `response.json().await`, mapowanie błędów. Nowy `/translate` użyje dokładnie tego wzorca.

> ⚠️ **Uwaga bezpieczeństwa**: istniejący klient w `auth.rs` używa `.danger_accept_invalid_certs(true)` — to ustawienie dev-only. Nowy klient do API tłumaczeń **nie** powinien tego kopiować.

**Wzorzec handlera**: dwa style współistnieją:
- `impl Responder` z walidacją wewnątrz (`handlers/collections.rs:35-70`).
- `Result<HttpResponse, AppError>` (`handlers/deploy.rs:7-69`) — **zalecany dla `/translate`**, bo dochodzi obsługa błędów API trzeciej strony.

**Wspólny typ błędu** `AppError` (`apps/backend/src/error.rs:5-34`) z wariantami `BadRequest`(400)/`Unauthorized`(401)/`Internal`(500)/`ServiceUnavailable`(503). Dla niedostępnego/awaryjnego API tłumaczeń → `ServiceUnavailable`.

**Ochrona JWT**: ekstraktor `AuthUser` (`apps/backend/src/auth.rs:45-82`) czyta `Authorization: Bearer`. `/translate` powinien być chroniony jak pozostałe endpointy.

**Walidacja języków** (do reużycia): `VALID_LANGUAGES = ["pl","en","de","es","fr","it"]` i `validate_languages(src, tgt)` (`apps/backend/src/handlers/collections.rs:9-13`), wymuszające `src != tgt`.

**Konfiguracja / env**: zmienne czytane w `apps/backend/src/main.rs:29-41` (`std::env::var`, `dotenv`), współdzielone przez `AppState` (`apps/backend/src/lib.rs:19-21`). Klucz API tłumaczeń (i region dla Azure) dodać jako nowe pola `AppState` + wpisy w `render.yaml:11-17` (`sync: false`). **Nie tworzyć `.env.example`** (`lessons.md:12-17`).

**DTO/serde**: wzorzec request struct w `apps/backend/src/models.rs:36-42`. Proponowane:
```rust
#[derive(Debug, Deserialize)]
pub struct TranslateRequest {
    pub source_text: String,
    pub source_language: String,
    pub target_language: String,
}
#[derive(Debug, Serialize)]
pub struct TranslateResponse {
    pub translated_text: String,
}
```

### B. Frontend KMP — podłączenie przycisku

**ApiClient** (`apps/frontend/shared/.../data/api/ApiClient.kt`): Ktor `HttpClient` z ContentNegotiation/JSON (`:24-35`), `API_BASE_URL` (`:18`), każda metoda `bearerAuth(requireToken())`. Wzorzec POST z body do naśladowania: `createFlashcard(...)` (`:60-64`).

**ApiModels** (`.../data/api/ApiModels.kt`): DTO `@Serializable` z `@SerialName` (snake_case w JSON). `FlashcardDto` (`:52-84`), `FlashcardRequest` (`:86-93`). Dodać `TranslateRequest`/`TranslateResponse` z `@SerialName("source_text")` itd.

**Repository** (`.../data/repository/FlashcardRepository.kt:10-45`): każda metoda `runCatching { ... } → Result<T>`, dekoduje `response.body()`. Wzorzec: `create(...)` (`:17-21`). Dodać `suspend fun translate(...): Result<String>`.

**DI** (`.../di/AppModule.kt`): `ApiClient` (`:22`), `FlashcardRepository` (`:25`), `FlashcardsViewModel` (`:28`) już zarejestrowane — **brak zmian**, nowa metoda repo dostępna przez istniejący `repo`.

**ViewModel** (`.../screens/flashcards/FlashcardsViewModel.kt`): `UiState` (`:13-18`), `viewModelScope.launch` + `.fold(onSuccess/onFailure)` (`:32-54`). Dodać do `UiState` flagi `isTranslating`/wynik i metodę `translateText(...)`.

**UI — `CardFormScreen.kt`**:
- Przycisk-stub „Przetłumacz" już istnieje, zablokowany, kolor `mute2` (`:249-291`) — uaktywnić i dodać `onClick`/loading.
- Ekran ma dostęp do `collection: CollectionDto` (`:66-71`), więc do `collection.sourceLanguage`/`targetLanguage` (`:361-362`).
- Stan formularza: `draft.sourceText` / `draft.targetText` w lokalnym `mutableStateOf` (`:100-111`).

**Reguły kierunku** (do implementacji w `onClick`):
```kotlin
val hasSource = draft.sourceText.isNotBlank()
val hasTarget = draft.targetText.isNotBlank()
val (fromLang, toLang, text, writeToTarget) = when {
    hasSource              -> Quad(collection.sourceLanguage, collection.targetLanguage, draft.sourceText, true)   // oba lub tylko source → Source→Target
    !hasSource && hasTarget-> Quad(collection.targetLanguage, collection.sourceLanguage, draft.targetText, false)  // tylko target → Target→Source
    else                   -> return@onClick                                                                      // oba puste
}
```
Przycisk `enabled = hasSource || hasTarget` (wyłączony tylko gdy oba puste). Wynik wpisać do pola docelowego (`writeToTarget`).

> 🐞 **Drobny dług do naprawienia przy okazji**: etykiety pól są zakodowane na sztywno jako `CapsLabel("POLSKI")` / `CapsLabel("ANGIELSKI")` (`CardFormScreen.kt:237,301`) zamiast korzystać z `LanguageNames[collection.sourceLanguage]` (`ui/components/Flag.kt:57-64`). Skoro kierunek tłumaczenia i tak zależy od języków kolekcji, warto przy tej zmianie zsynchronizować etykiety z rzeczywistymi językami kolekcji.

### C. Porównanie darmowych serwisów tłumaczeń (z kluczem API)

| Kryterium | **Azure Translator F0** (rekomendacja) | Google Cloud Translation v2 (fallback) | DeepL API Free |
|---|---|---|---|
| Darmowy limit | **2 000 000 zn./mies.** | 500 000 zn./mies. (odnawialne) | 500 000 zn./mies. |
| Karta przy rejestracji | TAK (+ weryfikacja tel.) | TAK (billing GCP) | TAK — ryzykowna weryfikacja kart spoza wąskiej listy krajów |
| Autoryzacja | `Ocp-Apim-Subscription-Key` + `Ocp-Apim-Subscription-Region` | `?key=<API_KEY>` w URL | `Authorization: DeepL-Auth-Key <key>` |
| Endpoint | `POST https://api.cognitive.microsofttranslator.com/translate?api-version=3.0` | `POST https://translation.googleapis.com/language/translate/v2` | `POST https://api-free.deepl.com/v2/translate` (inny host niż Pro!) |
| Body | `[{"Text":"..."}]`, `to`/`from` w query | `{"q":"...","target":"es","source":"pl","format":"text"}` | `{"text":["..."],"target_lang":"DE","source_lang":"PL"}` |
| Auto-detekcja | TAK (`detectedLanguage`) | TAK (`detectedSourceLanguage`) | TAK (`detected_source_language`) |
| Kody pl/en/de/es/fr/it | wszystkie, małe litery | wszystkie, małe litery | wszystkie, WIELKIE; EN target wymaga `EN-GB`/`EN-US` |
| Jakość (te pary) | bardzo dobra (NMT) | bardzo dobra (NMT) | najlepsza |
| Główna pułapka | region nagłówka musi zgadzać się z zasobem (inaczej 401/403) | domyślny `format=html` — ustawić `"text"` | wymóg karty; free host ≠ pro host |

**Rekomendacja: Azure Translator F0** — największy zapas limitu (przy krótkich fiszkach praktycznie nieosiągalny), prosta autoryzacja nagłówkowa pod `reqwest`, auto-detekcja, pełne wsparcie języków. **Fallback: Google v2.** Warto ukryć providera za jednym abstraktem w backendzie i wybierać go zmienną środowiskową — zmiana Azure↔Google to wtedy konfiguracja, nie kod.

#### Auto-detekcja a reguły kierunku
Wszystkie trzy serwisy potrafią same wykryć język źródłowy. Mimo to zalecam **jawne przekazywanie `source_language`/`target_language`** z języków kolekcji (są znane w UI) — daje to deterministyczne tłumaczenie zgodne z zadanymi regułami i pozwala backendowi reużyć `validate_languages()`.

#### Szkic wywołania Azure z `reqwest`
```rust
let url = format!("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from={from}&to={to}");
let resp: Vec<AzureResult> = client.post(&url)
    .header("Ocp-Apim-Subscription-Key", key)
    .header("Ocp-Apim-Subscription-Region", region)
    .header("Content-Type", "application/json; charset=UTF-8")
    .json(&json!([{ "Text": text }]))
    .send().await?.json().await?;
// resp[0].translations[0].text
```

## Code References

- `apps/backend/src/lib.rs:38-68` — rejestracja tras (`register_routes`); tu dodać scope `/translate`
- `apps/backend/src/lib.rs:19-21` — `AppState`; dodać pola klucz/region API tłumaczeń
- `apps/backend/src/handlers/mod.rs:1-6` — lista modułów handlerów; dodać `pub mod translate;`
- `apps/backend/src/handlers/auth.rs:17-47` — **wzorzec wywołania `reqwest`** (uwaga: `danger_accept_invalid_certs` jest dev-only)
- `apps/backend/src/handlers/collections.rs:9-13` — `VALID_LANGUAGES` + `validate_languages()` do reużycia
- `apps/backend/src/handlers/collections.rs:35-70` — wzorzec handlera `impl Responder`
- `apps/backend/src/handlers/deploy.rs:7-69` — wzorzec handlera `Result<HttpResponse, AppError>`
- `apps/backend/src/error.rs:5-34` — `AppError` (mapowanie na kody HTTP)
- `apps/backend/src/auth.rs:45-82` — ekstraktor JWT `AuthUser`
- `apps/backend/src/main.rs:29-41` — odczyt zmiennych env
- `apps/backend/src/models.rs:36-42` — wzorzec request DTO
- `render.yaml:11-17` — deklaracja env vars (`sync: false`)
- `apps/frontend/shared/src/commonMain/.../data/api/ApiClient.kt:18,24-35,60-64` — Ktor client + wzorzec POST
- `apps/frontend/shared/src/commonMain/.../data/api/ApiModels.kt:52-93` — DTO `@Serializable`/`@SerialName`
- `apps/frontend/shared/src/commonMain/.../data/repository/FlashcardRepository.kt:10-45` — wzorzec repo `Result<T>`
- `apps/frontend/shared/src/commonMain/.../di/AppModule.kt:22,25,28` — rejestracje Koin (bez zmian)
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/FlashcardsViewModel.kt:13-18,32-54` — wzorzec UiState/coroutine
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/CardFormScreen.kt:249-291` — **stub przycisku „Przetłumacz"**
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/CardFormScreen.kt:100-111` — stan `draft` (source/target)
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/CardFormScreen.kt:237,301` — zakodowane etykiety „POLSKI"/„ANGIELSKI" (dług)
- `apps/frontend/shared/src/commonMain/.../ui/components/Flag.kt:57-64` — `LanguageNames` (kod → polska nazwa)

## Architecture Insights

- **Backend jako jedyna brama do usług zewnętrznych** (`lessons.md:5-10`) — przesądza o proxy `/translate`; klucz tylko po stronie serwera.
- **Kompatybilność multiplatform „za darmo"** — wołając własny backend ze współdzielonego Ktor, nie ma osobnej integracji per platforma ani problemu z CORS na webie.
- **Spójna warstwa danych** — istnieją jednolite wzorce (Ktor client → `runCatching`/`Result` repo → `viewModelScope`/`fold` ViewModel), więc dodanie funkcji to powielenie istniejącego schematu, nie nowy mechanizm.
- **Abstrakcja providera** — ukrycie dostawcy tłumaczeń za jednym interfejsem w backendzie pozwala podmieniać Azure↔Google przez konfigurację.
- **Reguły kierunku należą do warstwy UI** — backend dostaje gotowe `source_language`/`target_language`; logika „które pole jest puste" zostaje w `CardFormScreen`.

## Historical Context (from prior changes)

- `context/archive/2026-05-29-collection-language-select/` — wprowadzenie wyboru języków kolekcji (`source_language`/`target_language`, kody pl/en/de/es/fr/it). To źródło języków dla kierunku tłumaczenia.
- `context/archive/2026-05-29-rename-flashcard-fields/` — zmiana nazw pól na `source_text`/`target_text` (migracja 007). Nowe DTO muszą używać tych nazw.
- `context/archive/2026-06-19-web-crud-service/` — ścieżka CRUD na webie; potwierdza, że współdzielona warstwa API działa na wszystkich platformach.

## Related Research

Brak wcześniejszych dokumentów `research.md` dotyczących tłumaczeń w `context/changes/**` ani `context/archive/**`.

## Open Questions

1. **Rejestracja Azure/Google z Polski** — czy uda się założyć konto F0 (wymóg karty + weryfikacja telefonu)? Jeśli nie, fallback to Google; DeepL jako ostatnia opcja.
2. **Język wyjściowy DeepL** — gdyby wybrać DeepL, target `en` wymaga wariantu `EN-GB`/`EN-US` (mapowanie kodów).
3. **Obsługa błędów w UI** — komunikat przy niedostępności API (503) / przekroczeniu limitu; toast vs inline.
4. **Nadpisywanie pola** — gdy oba pola wypełnione i użytkownik tłumaczy Source→Target, czy nadpisać istniejący Target bez potwierdzenia? (sugestia: tak, ale rozważyć).
5. **Trim/normalizacja** — przyciąć wynik i ewentualnie znormalizować wielkość liter dla pojedynczych słów.
