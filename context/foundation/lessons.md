# Lessons Learned

> Rejestr tylko do dodawania powtarzających się reguł i wzorców. Odczytywany ponownie na początku przez /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Aplikacja Android komunikuje się wyłącznie z backendem — nigdy bezpośrednio z Supabase

- **Context**: Architektura systemu — przepływ komunikacji między warstwami: Android app, backend Rust/Actix-web, Supabase (PostgreSQL + Auth)
- **Problem**: Jeśli aplikacja wywołuje Supabase bezpośrednio (przez SDK lub REST), klucze `anon`/`service_role` trafiają do klienta, logika autoryzacji duplikuje się w dwóch miejscach, a zmiana warstwy danych wymaga aktualizacji zarówno backendu, jak i aplikacji.
- **Rule**: Aplikacja Android komunikuje się wyłącznie z backendem przez REST API. Backend jest jedynym klientem Supabase. Żadne SDK Supabase ani bezpośrednie wywołania `supabase.co` nie mogą znajdować się w kodzie aplikacji.
- **Applies to**: `plan`, `implement`, `impl-review`

## Nigdy nie twórz pliku .env.example w repozytorium

- **Context**: Każdy moduł projektu (backend/, frontend/) — pliki konfiguracji środowiskowej
- **Problem**: Plik .env.example może przypadkowo zawierać prawdziwe sekrety lub ich fragmenty; wprowadza też zamieszanie co do tego, gdzie dokumentować zmienne środowiskowe i skłania do kopiowania go jako .env z błędnymi wartościami.
- **Rule**: Nigdy nie twórz pliku .env.example w repozytorium. Wymagane zmienne środowiskowe dokumentuj w render.yaml (jako `sync: false`) lub w README. Sekretów nie przechowuj nigdzie w repozytorium.
- **Applies to**: `implement`, `impl-review`
