---
project: FiszkiWBiegu
researched_at: 2026-05-25
recommended_platform: Render
runner_up: Fly.io
context_type: mvp
tech_stack:
  language: Rust + Kotlin (KMP)
  framework: Actix-web + Compose Multiplatform
  runtime: container (Docker)
  database: Supabase (PostgreSQL, zewnętrzny)
---

> **NIEAKTUALNE (2026-06-23):** Ten dokument to badanie z 2026-05-25. Render NIE został ostatecznie wybrany — backend działa na własnym serwerze (self-hosted) i jest aktualizowany przez self-update: GitHub Actions buduje statyczne binarium musl i wysyła je na endpoint `/deploy` (`X-Deploy-Key`). `render.yaml` w repo jest nieaktualny/nieużywany. Sekcje Supabase (baza danych, sqlx, RLS, JWT) pozostają aktualne. Aktualny stan wdrożenia: `context/foundation/roadmap.md` §Baza.

## Rekomendacja

**Wdróż backend na Render + Supabase jako bazę danych i dostawcę auth.**

Render uzyskał najwyższy wynik spośród platform obsługujących kontenery Rust (5/5 kryteriów przyjaznych agentom): publikuje `llms-full.txt` z pełną dokumentacją w markdownie, posiada oficjalny MCP server (GA od sierpnia 2025) i obsługuje rollback przez `render deploys create --commit <SHA>` z CLI. Supabase zastępuje Turso jako baza danych i dostarcza dodatkowo wbudowaną obsługę Google OAuth — eliminuje potrzebę ręcznej implementacji OAuth w Actix-web. Połączenie z backendem przez `sqlx` (PostgreSQL) z compile-time query checking. Oficjalny MCP server Supabase (`@supabase/mcp-server-supabase`) umożliwia agentowi zarządzanie schematem i migracjami. Koszt MVP: Render Starter ($7/mies.) + Supabase free tier ($0) = **~$7/mies.** Frontend (KMP Android + WASM) jest budowany lokalnie i dystrybuowany przez Google Play / hosting statyczny — nie wymaga oddzielnego wdrożenia serwerowego.

## Porównanie platform

Oceniano wyłącznie platformy obsługujące długo działające kontenery Rust. Cloudflare Workers, Vercel i Netlify zostały wyeliminowane twardym filtrem: nie obsługują persistent Docker containers dla Actix-web.

| Platforma | CLI-first | Managed | Docs dla agenta | Stabilne API deploy | MCP/Integracja | Razem |
|---|---|---|---|---|---|---|
| **Render** | ✅ Pass | ✅ Pass | ✅ Pass | ✅ Pass | ✅ Pass (GA) | **5/5** |
| **Fly.io** | ✅ Pass | ✅ Pass | ⚠️ Partial | ✅ Pass | ⚠️ Partial (EXPERIMENTAL) | **3.5/5** |
| **Railway** | ⚠️ Partial | ✅ Pass | ⚠️ Partial | ⚠️ Partial | ⚠️ Partial (WIP) | **3/5** |

### Platformy na krótkiej liście

#### 1. Render (Zalecana)

Jedyna platforma z pełnym pakietem: `render.com/llms.txt` + `render.com/docs/llms-full.txt` (pełna dokumentacja jako markdown), oficjalny MCP server (GA, sierpień 2025), natywny buildpack Rust + Docker multi-stage, rollback przez `render deploys create --commit <SHA>`. Z Supabase (free tier) całkowity koszt MVP = **~$7/mies.**

#### 2. Fly.io

Oficjalny przewodnik Actix-web (`fly.io/docs/rust/frameworks/actix-web/`), flyctl auto-generuje multi-stage Dockerfile z cargo-chef. Rollback deterministyczny przez `fly deploy --image <tag>`. Z Supabase zamiast MPG koszt spada do **~$4-6/mies.** (tylko VM). Słabości: brak `llms.txt`, MCP server EXPERIMENTAL. Dobra alternatywa z nieco lepszym rollbackiem niż Render.

#### 3. Railway

Najlepszy DX przy budowaniu (Railpack auto-detects Rust, BuildKit cache `~/.cargo` i `target/`), oficjalny MCP server (WIP). Z Supabase zamiast wbudowanego Postgres koszt spada do **~$5-7/mies.** Słabości: rollback tylko przez dashboard (brak CLI), jeden region EU (Amsterdam).

## Weryfikacja krzyżowa anty-uprzedzeniowa: Render + Supabase

### Adwokat diabła — Słabe strony

1. **Free tier pauzuje po 7 dniach bez aktywności** — projekty Supabase na free tier są automatycznie wstrzymywane po tygodniu braku żądań. Wymagane ręczne odblokowanie w dashboardzie lub upgrade do Pro ($25/mies.). Krytyczne podczas przerw w developmencie.
2. **Starter tier (0.5 vCPU) może być zbyt słaby** — przy skokach ruchu (poranne synchronizacje offline przed biegiem) 0.5 vCPU może być niewystarczające; upgrade do Standard ($25/mies.) podnosi koszt do ~$25/mies.
3. **Brak `render rollback` CLI** — nagły rollback wymaga znalezienia commit SHA i uruchomienia `render deploys create --commit <SHA>`; Dashboard rollback jest szybszy, ale wymaga przeglądarki.
4. **`sqlx` offline mode wymagany** — build environment Render nie ma sidecara DB; kompilacja z `sqlx::query!()` bez committed `.sqlx` katalogu lub `DATABASE_URL` podczas buildu zawiedzie.
5. **MCP server nie wyzwala deployów Render** — oficjalny MCP Render (GA) nie posiada narzędzia `deploy` ani `delete`; autonomiczne wdrożenia przez agenta wymagają CLI lub GitHub Actions webhook.

### Pre-Mortem — Jak to mogło się nie udać

Zespół wdrożył Rust/Actix-web na Render + Supabase w pierwszym tygodniu MVP. Po sześciu miesiącach napotykał narastające problemy. Pierwsze: deweloper wziął urlop na 10 dni — projekt Supabase na free tier zapauzował się. Po powrocie backend zwracał błędy połączenia z DB; dopiero po 20 minutach szukania przyczyny odkryto pauzowanie. Następnie natywne buildy Rust bez Docker multi-stage trwały 4-5 minut każdy — iteracja spowalniała.

Krytyczny moment: produkcyjny bug z tokenami JWT wymagał natychmiastowego rollbacku. Deweloper uruchomił `render deploys create --commit <SHA>`, ale nie wyłączył auto-deploy — następny push CI natychmiast nadpisał rollback. Incydent zajął 45 minut zamiast 2.

Nieoczekiwany problem z Supabase Auth: JWT tokeny wystawiane przez Supabase mają domyślny TTL 1 godzina. Android app nie odświeżała tokenów podczas biegu (offline mode) — po godzinie API zwracało 401, a użytkownik nie mógł zsynchronizować fiszek po powrocie do sieci.

### Nieznane niewiadome

- **Supabase free tier pauzowanie**: Projekty bezpłatne pauzują po 7 dniach bez aktywności — baza niedostępna do ręcznego odblokowania. W aktywnym developmencie wystarczy jedno żądanie dziennie, aby zapobiec pauzowaniu; w produkcji z prawdziwymi użytkownikami nie stanowi problemu.
- **Connection pooling via PgBouncer**: Supabase domyślnie eksponuje dwa connection stringi: direct (`port 5432`, session mode) i pooler (`port 6543`, transaction mode). Dla Actix-web z trwałymi połączeniami używaj session-mode pooler lub direct connection, nie transaction-mode (niezgodny z prepared statements `sqlx`).
- **`sqlx` offline mode**: Kompilacja z `sqlx::query!()` wymaga `DATABASE_URL` lub committed `.sqlx/` katalogu. Build environment Render nie ma dostępu do Supabase podczas buildu — wymagany `cargo sqlx prepare` lokalnie i commit `.sqlx/`.
- **JWT refresh podczas offline**: Supabase Auth JWT TTL = 1 godzina. Android app musi odświeżyć token przed wejściem w tryb offline lub obsłużyć 401 po powrocie do sieci.
- **Row Level Security (RLS)**: Supabase RLS jest domyślnie wyłączone. Jeśli backend używa `SUPABASE_ANON_KEY` (zamiast `SERVICE_ROLE_KEY`), brak włączonego RLS oznacza, że każdy użytkownik widzi dane wszystkich. Backend Actix-web powinien używać `SERVICE_ROLE_KEY` i egzekwować separację danych w logice aplikacji — nie polegać na RLS.

## Historia operacyjna

- **Wdrożenia podglądowe**: Render obsługuje Preview Environments (dla PR-ów) na planach Team/Enterprise — na Hobby/Starter brak natywnych preview. Workaround: osobne środowisko staging jako drugi web service z oddzielnym projektem Supabase (free tier pozwala na 2 projekty).
- **Sekrety**: `DATABASE_URL` (session-mode pooler URL Supabase), `SUPABASE_URL` i `SUPABASE_SERVICE_ROLE_KEY` przechowywane w Render Environment Variables (zaszyfrowane at rest). Edycja przez dashboard lub `render env set KEY=VALUE`. Rotacja: wygeneruj nowy `SERVICE_ROLE_KEY` w Supabase dashboard → zaktualizuj w Render → redeploy.
- **Wycofywanie**: `render deploys create --commit <SHA> --service <serviceID>` — wymaga SHA commitu. Dashboard: Events → Roll back (wyłącza auto-deploy). Czas rollbacku: szybki (cached build artifact). Uwaga: migracje schematu PostgreSQL NIE są cofane automatycznie — rollback kodu bez cofnięcia migracji może powodować błędy schematu.
- **Zatwierdzanie**: Agent może przez Render MCP: listować serwisy, pobierać logi, ustawiać env vars. Agent może przez Supabase MCP: uruchamiać SQL, zarządzać tabelami, listować migracje. Agent NIE może przez MCP: wyzwalać deployów Render, usuwać projektów Supabase. Produkcyjny deploy i destruktywne operacje DB wymagają potwierdzenia człowieka.
- **Logi**: `render logs -r <resourceID> --tail=true` — streaming w czasie rzeczywistym. Filtrowanie: `--level`, `--status-code`, `--path`. Retencja: 7 dni na Hobby; więcej na Professional.

## Rejestr ryzyka

| Ryzyko | Źródło | Prawdopodobieństwo | Wpływ | Łagodzenie |
|---|---|---|---|---|
| Supabase free tier pauzuje projekt | Nieznane niewiadome | Wysokie (dev przerwy) | Wysoki | Skonfiguruj cron ping co 5 dni LUB przejdź na Pro ($25) gdy projekt trafi do użytkowników |
| Starter (0.5 vCPU) zbyt słaby przy szczycie | Adwokat diabła | Średnie | Średni | Monitoruj CPU przez pierwsze 2 tygodnie; plan upgrade do Standard z góry |
| Rollback wymaga SHA + wyłączenia auto-deploy | Adwokat diabła | Wysokie (procedura) | Wysoki | Runbook: przed rollbackiem wyłącz auto-deploy w dashboard, potem `render deploys create --commit <SHA>` |
| `sqlx` offline mode — build failures | Nieznane niewiadome | Wysokie | Wysoki | `cargo sqlx prepare` lokalnie + commit `.sqlx/` do repo; w CI ustaw `SQLX_OFFLINE=true` |
| JWT wygasa podczas offline session na Androidzie | Pre-mortem | Średnie | Średni | Odświeżaj token przed startem trybu nauki; obsłuż 401 po powrocie do sieci jako trigger re-auth |
| RLS wyłączone = brak separacji danych przy anon key | Nieznane niewiadome | Niskie (backend używa service_role) | Wysoki | Backend zawsze używa `SUPABASE_SERVICE_ROLE_KEY`; nigdy `ANON_KEY` po stronie serwera; egzekwuj `user_id` w każdym zapytaniu |
| Brak GA object storage Render dla audio | Adwokat diabła | Niskie (MVP) | Średni | Supabase Storage (1 GB na free) dostępny jako fallback; w v2 jeśli TTS caching potrzebny |
| MCP Render nie wyzwala deployów | Nieznane niewiadome | Pewne | Niski | Deploy przez CLI lub GitHub Actions webhook; MCP używaj do monitoringu i logów |

## Rozpoczęcie pracy

1. **Zainstaluj Render CLI**: `npm install -g @render-sh/render-cli` lub `brew install render`.
2. **Utwórz projekt Supabase**: `supabase.com` → New project → Region: Central EU (Frankfurt) → zanotuj `Project URL` i `service_role` key.
3. **Pobierz connection string**: Supabase dashboard → Settings → Database → Connection string → Session mode (port 5432) → zapisz jako `DATABASE_URL`.
4. **Dodaj `sqlx` do backendu**: `cargo add sqlx --features runtime-tokio-rustls,postgres,uuid,chrono` → `cargo add supabase-auth-rs` (opcjonalnie, dla Supabase Auth JWT validation).
5. **Przygotuj sqlx offline**: `DATABASE_URL=<supabase_url> cargo sqlx prepare` → commit `.sqlx/` do repo.
6. **Utwórz web service na Render**: `render login` → `render services create --name fiszki-w-biegu-api --region frankfurt --runtime docker` → dodaj env vars: `DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`.
7. **Dodaj MCP servery do Claude Code**:
   - Render: `claude mcp add --transport http render https://mcp.render.com/mcp --header "Authorization: Bearer <RENDER_API_KEY>"`
   - Supabase: `claude mcp add supabase npx -y @supabase/mcp-server-supabase --project-ref <ref>`

Przed pierwszym deployem: sprawdź, że Actix-web binduje na `0.0.0.0:$PORT` i dodaj endpoint `/health` zwracający 200 OK bez zapytania do bazy danych.

## Poza zakresem

W niniejszych badaniach nie oceniano następujących kwestii:
- Konfiguracja Dockerfile i docker-compose
- Konfiguracja potoku CI/CD (GitHub Actions)
- Architektura na skalę produkcyjną (multi-region, HA, DR)
- Konfiguracja Supabase Auth Google OAuth (Client ID, Secret, Redirect URLs)
- Strategia migracji schematu PostgreSQL (supabase CLI migrations, sqlx-cli)
- Konfiguracja Row Level Security (RLS) jeśli architektura zmieni się na client-direct
