# Przycisk „Przetłumacz" w oknie definicji fiszki — Plan implementacji

## Przegląd

Podłączenie istniejącego (obecnie zablokowanego) przycisku „Przetłumacz" w oknie definicji fiszki do darmowego serwisu tłumaczeń. Tłumaczenie realizowane jako proxy przez backend Rust/Actix-web (zgodnie z regułą architektoniczną), z dostawcą **Microsoft Azure Translator (tier F0)** ukrytym za abstrakcją trait, wybieranym przez zmienną środowiskową. Kompatybilność Android/iOS/Web jest automatyczna — współdzielony kod KMP woła własny backend.

## Analiza stanu obecnego

- Przycisk „Przetłumacz" istnieje jako **zablokowany stub** (ikona `Translate`, kolor `mute2`) w `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/CardFormScreen.kt:249-291` — nie ma `onClick` ani logiki.
- Etykiety pól są zakodowane na sztywno: `CapsLabel("POLSKI")` (`CardFormScreen.kt:237`) i `CapsLabel("ANGIELSKI")` (`CardFormScreen.kt:301`), zamiast korzystać z języków kolekcji.
- Stan formularza trzymany lokalnie w `draft` (`CardFormScreen.kt:100-111`): pola `sourceText` / `targetText`.
- Backend nie ma endpointu tłumaczeń ani żadnej integracji z API tłumaczeń.
- Istnieją ugruntowane wzorce do reużycia: wychodzące `reqwest` (`apps/backend/src/handlers/auth.rs:17-47`), handler `Result<HttpResponse, AppError>` (`apps/backend/src/handlers/deploy.rs:7-69`), walidacja języków (`apps/backend/src/handlers/collections.rs:9-13`), odczyt env + `AppState` (`apps/backend/src/main.rs:29-41`, `apps/backend/src/lib.rs:19-21`), rejestracja tras (`apps/backend/src/lib.rs:38-68`).
- Frontend: Ktor client (`data/api/ApiClient.kt:18,24-35,60-64`), DTO `@Serializable/@SerialName` (`data/api/ApiModels.kt:52-93`), repo `runCatching/Result` (`data/repository/FlashcardRepository.kt:10-45`), ViewModel `viewModelScope/fold` (`screens/flashcards/FlashcardsViewModel.kt:13-18,32-54`), mapowanie kodów na nazwy `LanguageNames` (`ui/components/Flag.kt:57-64`).

## Pożądany stan końcowy

Użytkownik w oknie definicji fiszki, mając wpisany tekst w przynajmniej jednym polu, klika „Przetłumacz" i otrzymuje tłumaczenie wpisane do właściwego pola wg reguł kierunku. Pola pokazują rzeczywiste języki kolekcji. Weryfikacja: ręczny test w aplikacji Android dla kolekcji pl↔en oraz np. de↔es, w obu kierunkach, plus błąd przy niedostępności API; `cargo build --release` i `./gradlew :androidApp:assembleDebug` przechodzą.

### Kluczowe odkrycia:

- Wzorzec `reqwest` w `apps/backend/src/handlers/auth.rs:17-47` — do naśladowania, **ale bez** `.danger_accept_invalid_certs(true)` (dev-only).
- `VALID_LANGUAGES` + `validate_languages(src, tgt)` (`apps/backend/src/handlers/collections.rs:9-13`) wymusza `src != tgt` — reużyć.
- Azure: endpoint `POST https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from={from}&to={to}`, nagłówki `Ocp-Apim-Subscription-Key` + `Ocp-Apim-Subscription-Region`, body `[{"Text":"..."}]`, odpowiedź `[{"translations":[{"text":"..."}]}]`. Kody języków małymi literami — zgodne 1:1 z naszymi (`pl/en/de/es/fr/it`).
- `LanguageNames` (`ui/components/Flag.kt:57-64`) mapuje kod → polska nazwa (np. `pl`→`Polski`); użyć do etykiet.
- `CollectionDto` niesie `sourceLanguage`/`targetLanguage`, dostępne w `CardFormContent` przez parametr `collection`.

## Czego NIE robimy

- Nie hostujemy LibreTranslate ani nie wywołujemy API tłumaczeń bezpośrednio z klienta.
- Nie dodajemy drugiego dostawcy (Google/DeepL) jako implementacji — trait powstaje, ale implementujemy tylko Azure (miejsce na fallback zostaje).
- Nie piszemy testów automatycznych dla `/translate` (tylko weryfikacja ręczna; testy budowania/kompilacji pozostają).
- Nie tłumaczymy wsadowo wielu fiszek ani nie dodajemy auto-detekcji języka (przekazujemy jawne kody kolekcji).
- Nie zmieniamy schematu bazy danych.

## Podejście do implementacji

Klasyczna ścieżka pionowa: backend (konfiguracja → trait/dostawca → handler → trasa) → warstwa danych frontendu (DTO → client → repo) → UI/ViewModel. Dostawca ukryty za traitem `TranslationProvider`, wybierany zmienną `TRANSLATION_PROVIDER` (domyślnie `azure`), co umożliwia przyszły fallback bez zmian w handlerze. Reguły kierunku tłumaczenia żyją w UI; backend dostaje gotowe `source_language`/`target_language` i waliduje je tak jak kolekcje.

## Krytyczne szczegóły implementacji

- **Bezpieczeństwo klienta reqwest**: nowy klient HTTP do Azure NIE może kopiować `.danger_accept_invalid_certs(true)` z `auth.rs:17-47` — to ustawienie dev-only. Użyć domyślnej weryfikacji TLS.
- **Azure region w nagłówku**: brak lub niezgodny `Ocp-Apim-Subscription-Region` skutkuje 401/403 z Azure. Region jest osobną zmienną env, nie częścią klucza.
- **Nadpisywanie pola**: gdy oba pola wypełnione, tłumaczenie Source→Target nadpisuje istniejący Target bez potwierdzenia (zgodnie z regułą zadania).

## Faza 1: Backend — endpoint `/translate` (Azure za abstrakcją trait)

### Przegląd

Dodaje nowy chroniony JWT endpoint `POST /translate`, który waliduje kody języków i deleguje tłumaczenie do dostawcy wybranego przez env (Azure), wołając jego REST API przez `reqwest`.

### Wymagane zmiany:

#### 1. Konfiguracja środowiskowa i AppState

**Plik**: `apps/backend/src/main.rs`, `apps/backend/src/lib.rs`, `render.yaml`

**Cel**: Udostępnić handlerowi klucz API, region i wybór dostawcy tłumaczeń przez `AppState`, odczytane ze zmiennych środowiskowych. Udokumentować nowe zmienne w `render.yaml` (`sync: false`).

**Kontrakt**:
- Nowe zmienne env: `TRANSLATION_PROVIDER` (opcjonalna, domyślnie `azure`), `AZURE_TRANSLATOR_KEY`, `AZURE_TRANSLATOR_REGION`.
- `AppState` (`apps/backend/src/lib.rs:19-21`) zyskuje pola niosące konfigurację tłumaczeń (klucz, region, nazwa dostawcy).
- Odczyt w `apps/backend/src/main.rs:29-41` wzorem istniejących `std::env::var`; brak wymaganych zmiennych przy aktywnym dostawcy → jasny błąd startu lub kontrolowane wyłączenie endpointu (503).
- `render.yaml` (`:11-17`) dostaje nowe wpisy `- key: ...` z `sync: false`. **Nie tworzyć `.env.example`.**

#### 2. Abstrakcja dostawcy i implementacja Azure

**Plik**: `apps/backend/src/translation.rs` (nowy moduł; podpiąć w `apps/backend/src/lib.rs`)

**Cel**: Zdefiniować trait `TranslationProvider` z jedną operacją tłumaczenia oraz implementację dla Azure Translator. Wybór implementacji na podstawie `TRANSLATION_PROVIDER`.

**Kontrakt**:
- `trait TranslationProvider` z metodą async, np. `async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, AppError>`.
- `struct AzureTranslator { key, region, http: reqwest::Client }` implementujący trait.
- Funkcja-fabryka wybierająca dostawcę po nazwie z env (domyślnie `azure`); nieznana nazwa → `AppError::Internal`/`ServiceUnavailable`.
- Żądanie Azure: `POST https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from={source}&to={target}`, nagłówki `Ocp-Apim-Subscription-Key`, `Ocp-Apim-Subscription-Region`, `Content-Type: application/json; charset=UTF-8`, body `[{ "Text": text }]`.
- Deserializacja odpowiedzi (tablica → `translations[0].text`); pusta/niespodziewana odpowiedź lub status błędu → `AppError::ServiceUnavailable`.
- Klient `reqwest` z domyślnym TLS (bez `danger_accept_invalid_certs`).

**Kontrakt (szkic deserializacji — nietypowy kształt tablicy)**:
```rust
#[derive(Deserialize)]
struct AzureItem { translations: Vec<AzureTranslation> }
#[derive(Deserialize)]
struct AzureTranslation { text: String }
// resp: Vec<AzureItem> ; wynik = resp[0].translations[0].text
```

#### 3. DTO żądania/odpowiedzi

**Plik**: `apps/backend/src/models.rs`

**Cel**: Dodać modele request/response endpointu zgodne z konwencją snake_case (jak `CollectionRequest` `:36-42`).

**Kontrakt**:
- `TranslateRequest { source_text: String, source_language: String, target_language: String }` (`Deserialize`).
- `TranslateResponse { translated_text: String }` (`Serialize`).

#### 4. Handler `/translate`

**Plik**: `apps/backend/src/handlers/translate.rs` (nowy), `apps/backend/src/handlers/mod.rs`

**Cel**: Przyjąć żądanie, zwalidować języki i niepuste źródło, wywołać dostawcę i zwrócić przetłumaczony tekst; mapować błędy na właściwe kody HTTP.

**Kontrakt**:
- Sygnatura w stylu `pub async fn translate(state: web::Data<AppState>, user: AuthUser, body: web::Json<TranslateRequest>) -> Result<HttpResponse, AppError>` (ochrona JWT przez `AuthUser`, `apps/backend/src/auth.rs:45-82`).
- Reużycie `validate_languages()` (`apps/backend/src/handlers/collections.rs:9-13`) — import/współdzielenie; niepoprawne lub identyczne kody → 422.
- Puste `source_text` (po trim) → 400 (`AppError::BadRequest`).
- Sukces → `HttpResponse::Ok().json(TranslateResponse { translated_text })`.
- Błąd dostawcy/API → `AppError::ServiceUnavailable` (503).
- Dodać `pub mod translate;` w `apps/backend/src/handlers/mod.rs:1-6`.

#### 5. Rejestracja trasy

**Plik**: `apps/backend/src/lib.rs`

**Cel**: Zarejestrować `POST /translate` w `register_routes`.

**Kontrakt**: W `register_routes` (`apps/backend/src/lib.rs:38-68`) dodać `.route("/translate", web::post().to(handlers::translate::translate))` (lub scope `/translate`), spójnie z istniejącym stylem.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Cache zapytań aktualny: `cargo sqlx prepare` (w `apps/backend/`)
- [ ] Kompilacja release przechodzi: `cargo build --release` (w `apps/backend/`)

#### Weryfikacja ręczna:

- [ ] `curl -X POST .../translate` z poprawnym JWT i body zwraca przetłumaczony tekst (Azure)
- [ ] Identyczne lub spoza listy kody języków zwracają 422; puste `source_text` zwraca 400
- [ ] Brak/niepoprawny klucz lub region Azure skutkuje 503 (a nie crashem)

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 2.

---

## Faza 2: Frontend — warstwa danych

### Przegląd

Dodaje modele i metody klienta/repozytorium, by współdzielony kod KMP mógł wołać `POST /translate`.

### Wymagane zmiany:

#### 1. DTO tłumaczenia

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiModels.kt`

**Cel**: Dodać `@Serializable` modele żądania/odpowiedzi z mapowaniem na snake_case JSON backendu (wzór `FlashcardRequest` `:86-93`).

**Kontrakt**:
- `TranslateRequest(sourceText, sourceLanguage, targetLanguage)` z `@SerialName("source_text")`, `@SerialName("source_language")`, `@SerialName("target_language")`.
- `TranslateResponse(translatedText)` z `@SerialName("translated_text")`.

#### 2. Metoda klienta API

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/api/ApiClient.kt`

**Cel**: Dodać `suspend fun translate(...)` wykonującą POST na `/translate` z bearer i body JSON (wzór `createFlashcard` `:60-64`).

**Kontrakt**: `suspend fun translate(sourceText, sourceLanguage, targetLanguage): HttpResponse` — `client.post("$API_BASE_URL/translate") { bearerAuth(requireToken()); contentType(Json); setBody(TranslateRequest(...)) }`.

#### 3. Metoda repozytorium

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/FlashcardRepository.kt`

**Cel**: Dodać `suspend fun translate(...): Result<String>` w stylu `runCatching` zwracającym przetłumaczony tekst (wzór `create` `:17-21`).

**Kontrakt**: `runCatching { val r = api.translate(...); if (r.status.isSuccess()) r.body<TranslateResponse>().translatedText else error("HTTP ${r.status.value}") }`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Testy współdzielone przechodzą: `./gradlew :shared:test` (w `apps/frontend/`)

#### Weryfikacja ręczna:

- [ ] Brak — faza weryfikowana przez kompilację i Fazę 3

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 3.

---

## Faza 3: Frontend — UI i ViewModel

### Przegląd

Aktywuje przycisk „Przetłumacz" z regułami kierunku, podpina akcję ViewModel ze stanem ładowania/błędu, wyświetla inline błąd i naprawia etykiety języków.

### Wymagane zmiany:

#### 1. Akcja tłumaczenia w ViewModel

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/FlashcardsViewModel.kt`

**Cel**: Dodać do `FlashcardsUiState` stan ładowania/błędu tłumaczenia oraz metodę wołającą `repo.translate(...)` i zwracającą wynik do UI (wzór `loadFlashcards` `:32-54`).

**Kontrakt**:
- `FlashcardsUiState` (`:13-18`) zyskuje `isTranslating: Boolean = false` oraz `translationError: String? = null`.
- Metoda np. `translate(text, from, to, onResult: (String) -> Unit)` lub ekspozycja wyniku przez stan — wybór spójny z istniejącym wzorcem; ustawia `isTranslating`, w `onSuccess` przekazuje tekst do UI, w `onFailure` ustawia `translationError`.

#### 2. Aktywacja przycisku z regułami kierunku + inline błąd

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/CardFormScreen.kt`

**Cel**: Zamienić zablokowany stub (`:249-291`) na aktywny przycisk: włączony gdy przynajmniej jedno pole niepuste, z `onClick` wyznaczającym kierunek i wpisującym wynik do właściwego pola; pokazać stan ładowania i inline komunikat błędu pod przyciskiem.

**Kontrakt**:
- Reguły kierunku (na podstawie `draft`):
  - `sourceText` niepuste (samo lub oba) → tłumacz `collection.sourceLanguage` → `collection.targetLanguage`, wynik do `targetText` (nadpisuje istniejący bez pytania).
  - tylko `targetText` niepuste → tłumacz `collection.targetLanguage` → `collection.sourceLanguage`, wynik do `sourceText`.
  - oba puste → przycisk wyłączony.
- `enabled = draft.sourceText.isNotBlank() || draft.targetText.isNotBlank()`; podczas `isTranslating` przycisk pokazuje postęp/jest zablokowany.
- Po sukcesie `draft = draft.copy(...)` z przetłumaczonym tekstem w polu docelowym.
- `translationError` renderowany jako tekst (kolor `error`) pod wierszem przycisku.
- ViewModel dostępny w `CardFormScreen` przez `koinViewModel(...)` (`:69`); przekazać akcję/stan do `CardFormContent` analogicznie do `CardFormActions`.

#### 3. Etykiety języków z kolekcji

**Plik**: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/screens/flashcards/CardFormScreen.kt`

**Cel**: Zastąpić zakodowane `CapsLabel("POLSKI")` (`:237`) i `CapsLabel("ANGIELSKI")` (`:301`) nazwami z `LanguageNames` na podstawie języków kolekcji; flagi również wg kodów kolekcji.

**Kontrakt**:
- Górne pole: `Flag(collection.sourceLanguage, ...)` + `CapsLabel(LanguageNames[collection.sourceLanguage]?.uppercase() ?: collection.sourceLanguage.uppercase())`.
- Dolne pole: analogicznie dla `collection.targetLanguage`.
- `LanguageNames` z `ui/components/Flag.kt:57-64`.

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- [ ] Testy współdzielone przechodzą: `./gradlew :shared:test` (w `apps/frontend/`)
- [ ] APK debug buduje się: `./gradlew :androidApp:assembleDebug` (w `apps/frontend/`)

#### Weryfikacja ręczna:

- [ ] Kolekcja pl→en: wpisz tylko Source → klik tłumaczy do Target; wpisz tylko Target → tłumaczy do Source; oba wypełnione → tłumaczy Source→Target (nadpisuje Target); oba puste → przycisk wyłączony
- [ ] Kolekcja innych języków (np. de→es): etykiety i flagi pokazują właściwe języki, tłumaczenie działa w obu kierunkach
- [ ] Przy niedostępnym API: inline komunikat błędu pojawia się pod przyciskiem, formularz pozostaje użyteczny
- [ ] Wskaźnik ładowania widoczny podczas tłumaczenia

**Uwaga implementacyjna**: Po przejściu weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie zakończenia.

---

## Strategia testowania

### Testy jednostkowe:

- Brak nowych testów automatycznych (decyzja: tylko weryfikacja ręczna). Istniejące `./gradlew :shared:test` muszą nadal przechodzić.

### Kroki testowania ręcznego:

1. Uruchom backend z ustawionymi `AZURE_TRANSLATOR_KEY` i `AZURE_TRANSLATOR_REGION`.
2. W aplikacji Android otwórz okno nowej/edycji fiszki w kolekcji pl→en.
3. Przetestuj wszystkie cztery reguły kierunku (tylko source / tylko target / oba / oba puste).
4. Powtórz dla kolekcji o innych językach (np. de→es) i sprawdź etykiety/flagi.
5. Zasymuluj błąd (zły klucz/region lub brak sieci) i potwierdź inline komunikat.

## Uwagi dotyczące wydajności

Krótkie teksty (słowa/frazy), niski wolumen — limit Azure F0 (2 mln zn./mies.) praktycznie nieosiągalny. Brak potrzeby cache'owania.

## Uwagi dotyczące migracji

Brak zmian w bazie danych. Wdrożenie wymaga ustawienia nowych zmiennych env na Render (`AZURE_TRANSLATOR_KEY`, `AZURE_TRANSLATOR_REGION`, opcjonalnie `TRANSLATION_PROVIDER`).

## Referencje

- Powiązane badania: `context/changes/flashcard-translate/research.md`
- Wzór reqwest: `apps/backend/src/handlers/auth.rs:17-47`
- Wzór handlera z AppError: `apps/backend/src/handlers/deploy.rs:7-69`
- Walidacja języków: `apps/backend/src/handlers/collections.rs:9-13`
- Wzór repo: `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/data/repository/FlashcardRepository.kt:17-21`

## Postęp

> Konwencja: `- [ ]` oczekujące, `- [x]` wykonane. Dołącz ` — <commit sha>` po zakończeniu kroku. Nie zmieniaj nazw tytułów kroków. Zobacz `references/progress-format.md`.

### Faza 1: Backend — endpoint /translate

#### Automatyczne

- [x] 1.1 Cache zapytań aktualny: `cargo sqlx prepare` — c3c906a
- [x] 1.2 Kompilacja release przechodzi: `cargo build --release` — c3c906a

#### Ręczne

- [ ] 1.3 POST /translate z poprawnym JWT zwraca przetłumaczony tekst (Azure)
- [ ] 1.4 Niepoprawne/identyczne kody → 422; puste source_text → 400
- [ ] 1.5 Brak/niepoprawny klucz lub region → 503 (bez crasha)

### Faza 2: Frontend — warstwa danych

#### Automatyczne

- [x] 2.1 Testy współdzielone przechodzą: `./gradlew :shared:test` — 9386522

### Faza 3: Frontend — UI i ViewModel

#### Automatyczne

- [x] 3.1 Testy współdzielone przechodzą: `./gradlew :shared:test`
- [x] 3.2 APK debug buduje się: `./gradlew :androidApp:assembleDebug`

#### Ręczne

- [ ] 3.3 Kolekcja pl→en: cztery reguły kierunku działają poprawnie
- [ ] 3.4 Kolekcja innych języków (de→es): etykiety/flagi i tłumaczenie w obu kierunkach
- [ ] 3.5 Niedostępne API: inline błąd pod przyciskiem, formularz użyteczny
- [ ] 3.6 Wskaźnik ładowania widoczny podczas tłumaczenia
