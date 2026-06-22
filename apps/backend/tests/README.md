# Testy integracyjne backendu

Testy integracyjne uruchamiają **realny serwer** in-process i sprawdzają każdy endpoint
przez HTTP (`reqwest`), weryfikując wynik bezpośrednio w bazie PostgreSQL uruchamianej
w kontenerze Docker (`testcontainers`, obraz `postgres:16-alpine`).

## Wymagania

- **Działający Docker** (daemon dostępny dla `testcontainers`).
- Obraz `postgres:16-alpine` (zostanie pobrany automatycznie, jeśli go nie ma).

## Uruchomienie (tylko lokalnie)

```bash
cd apps/backend
cargo test
```

Pojedynczy zestaw:

```bash
cargo test collections
cargo test flashcards
cargo test learning
cargo test deploy
cargo test auth
```

## CI

Testy **nie uruchamiają się w CI**. Workflow `backend-deploy.yml` wykonuje wyłącznie
`cargo build --release`, a `cargo build` nie kompiluje ani targetów testowych, ani
zależności deweloperskich (`testcontainers`) — więc Docker w CI nie jest potrzebny.

## Co jest testowane

- Wszystkie endpointy aplikacyjne (`/collections`, `/flashcards`, `/learning`) — pozytywnie
  i negatywnie (błędne/niepełne dane, własność, brak JWT).
- Ścieżki odrzucenia `POST /deploy` (503/401/400) — **nigdy** happy-path (podmienia binarkę).
- Przekrojowe testy uwierzytelniania (401).

Pominięte celowo: `POST /auth/login` (zależny od zewnętrznego Google) i happy-path `/deploy`.

## Konwencja

- Użytkownicy są wstawiani bezpośrednio do DB; cała pozostała funkcjonalność idzie przez REST.
- Każdy test seeduje własnego usera (unikalny `google_id`) — izolacja na współdzielonym kontenerze.
- Znane bugi/niespójności są zasertowane jako **obecne** zachowanie i oznaczone `KNOWN ISSUE`.
