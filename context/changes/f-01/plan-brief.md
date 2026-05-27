# F-01: Cache i synchronizacja fiszek offline — Krótki plan

> Pełny plan: `context/changes/f-01/plan.md`

## Co i dlaczego

Dodajemy reaktywne wykrywanie łączności sieciowej i blokujemy przycisk "▶ Nauka" gdy urządzenie jest offline. Bez tej blokady użytkownik może nacisnąć "Start" bez sieci — aplikacja cicho nie ładuje fiszek i pokazuje nieskończony spinner w LearningScreen. F-01 jest wymaganiem wstępnym dla S-02 (audio learning session).

## Punkt wyjścia

Przepływ offline działa już po starcie sesji: `LearningService` trzyma fiszki w pamięci, TTS nie wymaga sieci — NFR "po uruchomieniu sesji" jest spełnione. Brakuje tylko bariery przed startem sesji gdy brak połączenia. Przycisk "▶ Nauka" (`FlashcardsScreen.kt:73-76`) jest dziś blokowany tylko gdy lista fiszek jest pusta.

## Pożądany stan końcowy

Przycisk "▶ Nauka" wyłącza się reaktywnie gdy urządzenie traci łączność i ponownie aktywuje po jej odzyskaniu — bez restartu aplikacji. Raz uruchomiona sesja nauki kontynuuje działanie offline bez zmian.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
| ------- | ----- | ------------------- | ------ |
| Technologia cache | In-memory (brak persystencji) | LearningService już trzyma fiszki; raz załadowane = offline przez czas sesji | Plan |
| Trigger synchronizacji | Przy starcie sesji nauki (API call w LearningViewModel) | Minimalna zmiana; sesja offline potrzebna tylko po jej uruchomieniu | Plan |
| Zakres offline | Tylko sesja nauki (read-only) | PRD NFR mówi "po uruchomieniu sesji", nie przy CRUD | Plan |
| NetworkChecker | Interfejs commonMain + Android impl | Wzorzec identyczny jak LearningController; zero nowych zależności | Plan |
| Staleness | Brak TTL — cache nadpisywany przy każdym online fetch | Prosty model mentalny, wystarczający dla MVP | Plan |
| UX po offline-start | Blokada przycisku (nie komunikat błędu) | Czytelniejsze niż reaktywny error w LearningScreen | Plan |
| App kill recovery | Restart wymaga sieci | Foreground Service trzyma sesję; edge case akceptowalny dla MVP | Plan |

## Zakres

**W zakresie:**
- Interfejs `NetworkChecker` (commonMain) + `AndroidNetworkChecker` (ConnectivityManager)
- Koin wiring w `androidModule`
- `ACCESS_NETWORK_STATE` w AndroidManifest
- Blokada "▶ Nauka" w `FlashcardsScreen` gdy offline

**Poza zakresem:**
- Persystencja fiszek na dysk (Room, SQLDelight, multiplatform-settings + JSON)
- Offline CRUD fiszek
- Wskaźnik "ostatnia synchronizacja" / "tryb offline"
- Error state w LearningScreen dla nieudanego fetch
- Retry logic / backoff w ApiClient

## Architektura / Podejście

```
commonMain: NetworkChecker (interface)
                    ↓ implementuje
androidApp: AndroidNetworkChecker (ConnectivityManager.registerNetworkCallback)
                    ↓ Koin single<NetworkChecker>
FlashcardsScreen: koinInject<NetworkChecker>()
                    ↓ collectAsState()
"▶ Nauka" button: enabled = flashcards.isNotEmpty() && isOnline
```

Dokładnie ten sam wzorzec co `LearningController` → `AndroidLearningController`.

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
| ---- | ------------ | --------------- |
| 1. NetworkChecker | Reaktywny StateFlow\<Boolean\> dostępny przez Koin | Uprawnienie ACCESS_NETWORK_STATE wymagane w Manifest |
| 2. Offline guard | Przycisk blokowany gdy offline, reaktywna aktualizacja | Brak (prosta zmiana w jednym pliku) |

**Wymagania wstępne:** Działający emulator/urządzenie Android  
**Szacowany nakład pracy:** ~1 sesja, 2 fazy, ~4 nowe/zmienione pliki

## Otwarte ryzyka i założenia

- `onLost` callback nie zawsze oznacza brak internetu (WiFi off, ale mobilne dane aktywne) — implementacja używa `isCurrentlyOnline()` zamiast hardkodowanego `false`.
- Koin wiring musi być w `androidModule` (nie `appModule`) bo `androidContext()` jest niedostępny w commonMain.

## Kryteria sukcesu (podsumowanie)

- Tryb samolotowy → "▶ Nauka" wyszarzony reaktywnie (bez restartu app)
- Wyłączenie trybu samolotowego → przycisk ponownie aktywny
- Sesja nauki uruchomiona online → utrata sieci w trakcie → sesja kontynuuje
