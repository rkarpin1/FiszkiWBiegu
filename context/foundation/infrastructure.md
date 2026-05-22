---
project: FiszkiWBiegu
researched_at: 2026-05-22
recommended_platform: Render
runner_up: Fly.io
context_type: mvp
tech_stack:
  language: Rust + Kotlin (KMP)
  framework: Actix-web + Compose Multiplatform
  runtime: container (Docker)
  database: Turso (libSQL, zewnętrzny)
---

## Rekomendacja

**Wdróż backend na Render + Turso jako bazę danych.**

Render uzyskał najwyższy wynik spośród platform obsługujących kontenery Rust (5/5 kryteriów przyjaznych agentom): publikuje `llms-full.txt` z pełną dokumentacją w markdownie, posiada oficjalny MCP server (GA od sierpnia 2025) i obsługuje rollback przez `render deploys create --commit <SHA>` z CLI. Turso zastępuje Postgres jako baza danych: model danych FiszkiWBiegu (Users → Collections → Flashcards) to prosty CRUD bez złożonych joinów, gdzie libSQL jest w pełni wystarczający. Dodatkowa korzyść: Turso embedded replica rozwiązuje open question #2 z PRD (synchronizacja offline) — Android app może mieć lokalną replikę libSQL synchronizowaną z chmurą przy starcie. Koszt MVP: Render Starter ($7/mies.) + Turso free tier ($0) = **~$7/mies.** Frontend (KMP Android + WASM) jest budowany lokalnie i dystrybuowany przez Google Play / hosting statyczny — nie wymaga oddzielnego wdrożenia serwerowego.

## Porównanie platform

Oceniano wyłącznie platformy obsługujące długo działające kontenery Rust. Cloudflare Workers, Vercel i Netlify zostały wyeliminowane twardym filtrem: nie obsługują persistent Docker containers dla Actix-web.

| Platforma | CLI-first | Managed | Docs dla agenta | Stabilne API deploy | MCP/Integracja | Razem |
|---|---|---|---|---|---|---|
| **Render** | ✅ Pass | ✅ Pass | ✅ Pass | ✅ Pass | ✅ Pass (GA) | **5/5** |
| **Fly.io** | ✅ Pass | ✅ Pass | ⚠️ Partial | ✅ Pass | ⚠️ Partial (EXPERIMENTAL) | **3.5/5** |
| **Railway** | ⚠️ Partial | ✅ Pass | ⚠️ Partial | ⚠️ Partial | ⚠️ Partial (WIP) | **3/5** |

### Platformy na krótkiej liście

#### 1. Render (Zalecana)

Jedyna platforma z pełnym pakietem: `render.com/llms.txt` + `render.com/docs/llms-full.txt` (pełna dokumentacja jako markdown), oficjalny MCP server (GA, sierpień 2025), natywny buildpack Rust + Docker multi-stage, rollback przez `render deploys create --commit <SHA>`. Z Turso jako zewnętrzną bazą koszt MVP to Starter ($7/mies.) + Turso free ($0) = **~$7/mies.**

#### 2. Fly.io

Oficjalny przewodnik Actix-web (`fly.io/docs/rust/frameworks/actix-web/`), flyctl auto-generuje multi-stage Dockerfile z cargo-chef. Rollback deterministyczny przez `fly deploy --image <tag>`. Z Turso zamiast MPG koszt spada do **~$4-6/mies.** (tylko VM). Słabości: brak `llms.txt`, MCP server EXPERIMENTAL. Dobra alternatywa z nieco lepszym rollbackiem niż Render.

#### 3. Railway

Najlepszy DX przy budowaniu (Railpack auto-detects Rust, BuildKit cache `~/.cargo` i `target/`), oficjalny MCP server (WIP). Z Turso zamiast wbudowanego Postgres koszt spada do **~$5-7/mies.** Słabości: rollback tylko przez dashboard (brak CLI), jeden region EU (Amsterdam).

## Weryfikacja krzyżowa anty-uprzedzeniowa: Render + Turso

### Adwokat diabła — Słabe strony

1. **Brak GA object storage** — Render Object Storage jest w Alpha (region-limited). Jeśli projekt doda cache audio TTS lub attachmenty, wymagany zewnętrzny S3 (Cloudflare R2, AWS S3).
2. **Starter tier (0.5 vCPU) może być zbyt słaby** — przy skokach ruchu (poranne synchronizacje offline przed biegiem) 0.5 vCPU może być niewystarczające; upgrade do Standard ($25/mies.) podnosi koszt do ~$25/mies.
3. **Brak `render rollback` CLI** — nagły rollback wymaga znalezienia commit SHA i uruchomienia `render deploys create --commit <SHA>`; Dashboard rollback jest szybszy, ale wymaga przeglądarki.
4. **`libsql` crate jest mniej dojrzały niż `sqlx`** — mniej przykładów dla Actix-web, brak compile-time query checking (`sqlx::query!()`), mniejsza społeczność Rust.
5. **MCP server nie wyzwala deployów** — oficjalny MCP Render (GA) nie posiada narzędzia `deploy` ani `delete`; autonomiczne wdrożenia przez agenta wymagają CLI lub GitHub Actions webhook.

### Pre-Mortem — Jak to mogło się nie udać

Zespół wdrożył Rust/Actix-web na Render + Turso w pierwszym tygodniu MVP. Po sześciu miesiącach napotykał narastające problemy. Pierwsze: natywne buildy Rust bez Docker multi-stage trwały 4-5 minut każdy — iteracja spowalniała. Następnie Starter (0.5 vCPU) zaczął przeciążać się przy synchronizacjach offline kilku użytkowników równocześnie przed porannym biegiem; upgrade do Standard podwoił koszt obliczeniowy.

Krytyczny moment: produkcyjny bug z tokenami OAuth wymagał natychmiastowego rollbacku. Deweloper uruchomił `render deploys create --commit <SHA>`, ale nie wyłączył auto-deploy — następny push CI natychmiast nadpisał rollback. Incydent zajął 45 minut zamiast 2.

Nieoczekiwany problem z Turso: embedded replica na Androidzie przestała się synchronizować po rotacji tokenu autoryzacyjnego — aplikacja serwowała użytkownikom fiszki sprzed tygodnia bez widocznego błędu. Brak mechanizmu wykrywania stale replica po stronie libsql-android sprawił, że bug był trudny do zdiagnozowania.

### Nieznane niewiadome

- **Workspace plan fee (od kwietnia 2026)**: Professional workspace ($19/użytkownik/mies.) wymagany dla retencji logów >7 dni i priorytetowego wsparcia — nieoczywisty koszt przy skalowaniu projektu.
- **Health check przed routingiem**: Render nie kieruje ruchu do nowej instancji dopóki health check nie zwróci 200 OK w oknie 180s. Jeśli Actix-web uruchamia migracje libSQL przy starcie, timeout może upłynąć — silent deployment failure.
- **MCP deploy gap**: Oficjalny MCP Render nie może wyzwalać deployów. Pełna automatyzacja CI/CD wymaga GitHub Actions lub CLI — nie czystego MCP.
- **Turso embedded replica a rotacja tokenu**: Jeśli `TURSO_AUTH_TOKEN` wygaśnie lub zostanie zrotowany, embedded replica na Androidzie przestaje się synchronizować cicho — aplikacja serwuje nieaktualne dane bez błędu widocznego dla użytkownika. Wymagany mechanizm wykrywania staleness.
- **libsql a migracje schematu**: Turso nie ma natywnego narzędzia migracji (jak sqlx-cli). Migracje to ręczne pliki SQL wykonywane przy starcie lub przez osobny skrypt — brak rollbacku migracji out-of-the-box.

## Historia operacyjna

- **Wdrożenia podglądowe**: Render obsługuje Preview Environments (dla PR-ów) na planach Team/Enterprise — na Hobby/Starter brak natywnych preview. Workaround: osobne środowisko staging jako drugi web service z osobną bazą Turso.
- **Sekrety**: `TURSO_DATABASE_URL` i `TURSO_AUTH_TOKEN` przechowywane w Render Environment Variables (zaszyfrowane at rest). Edycja przez dashboard lub `render env set KEY=VALUE`. Rotacja tokenu Turso: wygeneruj nowy w dashboardzie Turso → zaktualizuj w Render → redeploy.
- **Wycofywanie**: `render deploys create --commit <SHA> --service <serviceID>` — wymaga SHA commitu. Dashboard: Events → Roll back (wyłącza auto-deploy). Czas rollbacku: szybki (cached build artifact). Uwaga: migracje schematu libSQL NIE są cofane automatycznie — rollback kodu bez cofnięcia migracji może powodować błędy schematu.
- **Zatwierdzanie**: Agent może: listować serwisy, pobierać logi, ustawiać env vars, uruchamiać deploy przez commit SHA (przez CLI/API). Agent NIE może przez MCP: wyzwalać deployów, usuwać zasobów, modyfikować planów. Produkcyjny deploy powinien wymagać potwierdzenia człowieka.
- **Logi**: `render logs -r <resourceID> --tail=true` — streaming w czasie rzeczywistym. Filtrowanie: `--level`, `--status-code`, `--path`, `--start`/`--end`. Retencja: 7 dni na Hobby; więcej na Professional.

## Rejestr ryzyka

| Ryzyko | Źródło | Prawdopodobieństwo | Wpływ | Łagodzenie |
|---|---|---|---|---|
| Starter (0.5 vCPU) zbyt słaby przy szczycie | Adwokat diabła | Średnie | Średni | Monitoruj CPU przez pierwsze 2 tygodnie; plan upgrade do Standard z góry |
| Rollback wymaga SHA + wyłączenia auto-deploy | Adwokat diabła | Wysokie (procedura) | Wysoki | Runbook: przed rollbackiem wyłącz auto-deploy w dashboard, potem `render deploys create --commit <SHA>` |
| MCP nie wyzwala deployów | Nieznane niewiadome | Pewne | Niski | Deploy przez CLI lub GitHub Actions webhook; MCP używaj do monitoringu i logów |
| Brak GA object storage dla audio | Adwokat diabła | Niskie (MVP) | Średni | W MVP nie ma audio caching — jeśli w v2 potrzebne, dodaj Cloudflare R2 |
| Health check timeout przy starcie z migracjami | Nieznane niewiadome | Średnie | Wysoki | Dodaj `/health` endpoint bez zależności od DB; migracje jako osobny krok przed startem serwera |
| Turso embedded replica stale po rotacji tokenu | Pre-mortem | Średnie | Wysoki | Implementuj mechanizm wykrywania staleness w Android app; loguj błędy sync do zdalnego systemu |
| libsql brak narzędzia migracji | Nieznane niewiadome | Pewne | Średni | Używaj plików SQL w `migrations/` wykonywanych przez `libsql` przy starcie lub przez osobny binary; wersjonuj pliki numerycznie |
| `libsql` crate mniej dojrzały niż `sqlx` | Adwokat diabła | Średnie | Średni | Weryfikuj kompatybilność Actix-web + libsql przed implementacją; fallback: użyj sqlx z SQLite feature lokalnie, libsql remote w produkcji |

## Rozpoczęcie pracy

1. **Zainstaluj Render CLI**: `npm install -g @render-sh/render-cli` lub `brew install render`.
2. **Zaloguj się**: `render login` (otwiera przeglądarkę; generuje token API).
3. **Utwórz web service**: `render services create --name fiszki-w-biegu-api --region frankfurt --runtime docker --repo https://github.com/rkarpin1/FiszkiWBiegu --branch master`.
4. **Utwórz bazę Turso**: `turso db create fiszki-w-biegu --location fra` → `turso db tokens create fiszki-w-biegu`. Zapisz URL i token jako `TURSO_DATABASE_URL` i `TURSO_AUTH_TOKEN` w Render Environment Variables.
5. **Dodaj `libsql` do backendu**: `cargo add libsql` — połączenie: `Builder::new_remote(url, token).build().await`.
6. **Dodaj MCP server do Claude Code**: `claude mcp add --transport http render https://mcp.render.com/mcp --header "Authorization: Bearer <RENDER_API_KEY>"`.

Przed pierwszym deployem: sprawdź, że Actix-web binduje na `0.0.0.0:$PORT` (nie `127.0.0.1:8080`) i dodaj endpoint `/health` zwracający 200 OK bez zależności od bazy danych.

## Poza zakresem

W niniejszych badaniach nie oceniano następujących kwestii:
- Konfiguracja Dockerfile i docker-compose
- Konfiguracja potoku CI/CD (GitHub Actions)
- Architektura na skalę produkcyjną (multi-region, HA, DR)
- Konfiguracja OAuth Google Sign-In (callback URLs, credentials)
- Strategia migracji schematu libSQL (narzędzie do wersjonowania plików SQL)
- Konfiguracja Turso embedded replica po stronie Android (libsql-android SDK)
