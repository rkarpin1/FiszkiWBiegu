# Automatyczne wylogowanie przy 401 — Krótki plan

> Pełny plan: `context/changes/s-02a/plan.md`

## Co i dlaczego

Gdy JWT wygaśnie, backend zwraca 401. Aplikacja nie obsługuje tego — użytkownik widzi pętlę błędów w Snackbar bez możliwości wyjścia. Dodajemy Ktor interceptor + `AuthEventBus` (SharedFlow), by automatycznie wylogować użytkownika i przenieść go na `LoginScreen`.

## Punkt wyjścia

`ApiClient` ma guard `requireToken()` (brak tokenu → wyjątek), ale nie reaguje na wygasły token zwrócony przez backend. `App.kt` ma już logikę `authRepository.logout() + destination = Destination.Login` w `onLogout` — potrzebujemy tylko podłączyć ją do zdarzenia 401.

## Pożądany stan końcowy

Po wygaśnięciu tokenu: pierwsze wywołanie API zwraca 401 → aplikacja cicho przechodzi na `LoginScreen`. Brak komunikatu błędu. Zachowanie identyczne z "zimnym startem".

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---------|-------|---------------------|--------|
| Gdzie żyje SharedFlow | Nowa klasa `AuthEventBus` | Brak zależności cyklicznej; `ApiClient` nie wystawia flow jako swojego API | Plan |
| Kto czyści token | `App.kt` przez `AuthRepository.logout()` | Jeden punkt odpowiedzialny za logout; spójne z istniejącym `onLogout` | Plan |
| UX po 401 | Cichy redirect | Zero nowego stanu; spójne z "zimnym startem" | Plan |
| Zakres interceptora | Tylko 401 | 403 nie powinien wystąpić przy obecnej architekturze backendu | Plan |

## Zakres

**W zakresie:** Ktor HttpCallValidator, AuthEventBus (SharedFlow), wiring w App.kt

**Poza zakresem:** Token refresh, komunikat "Sesja wygasła", obsługa 403, zmiany backendu

## Architektura

```
ApiClient (HttpCallValidator)
    → 401 detected
    → AuthEventBus.emitUnauthorized()
    → SharedFlow<Unit>
    → App.kt (LaunchedEffect)
    → authRepository.logout() + destination = Destination.Login
```

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. AuthEventBus + interceptor | AuthEventBus, HttpCallValidator w ApiClient, Koin | Ktor API `validateResponse` — nie rzucać wyjątku |
| 2. App.kt wiring + E2E | LaunchedEffect w App.kt, weryfikacja na urządzeniu | Miejsce LaunchedEffect (poza `when`) |

**Wymagania wstępne:** Zalogowany użytkownik, urządzenie/emulator Android  
**Szacowany nakład pracy:** ~1 sesja, 2 fazy, 4 małe pliki

## Otwarte ryzyka i założenia

- Backend konsekwentnie zwraca 401 (nie inny kod) przy wygasłym tokenie — zakładamy spójność JWT validation
- `validateResponse` w Ktor 3.5.0 jest suspend — umożliwia `emit()` bez `tryEmit()`

## Kryteria sukcesu

- Nieważny token → cichy redirect do LoginScreen bez komunikatu błędu
- Po ponownym zalogowaniu app działa normalnie
- Brak regresji w istniejących flow (kolekcje, fiszki, nauka)
