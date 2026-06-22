---
change_id: integration-tests
title: Testy integracyjne backendu (Docker Postgres + REST)
status: impl_reviewed
created: 2026-06-22
updated: 2026-06-22
---

## Goal

Napisać testy integracyjne dla backendu Rust/Actix-web w katalogu `apps/backend/tests/`. Testy uruchamiają realny serwer i sprawdzają wszystkie endpointy przez REST, a wynik weryfikują bezpośrednio w bazie PostgreSQL uruchamianej w kontenerze Docker (`testcontainers`, image `postgres:16-alpine`).

## Scope

- Baza PostgreSQL w Dockerze przez crate `testcontainers` (izolowana per uruchomienie, migracje 001–010 stosowane automatycznie).
- Użytkownicy wstawiani bezpośrednio do DB; pozostała funkcjonalność testowana wyłącznie via REST, wynik porównywany w DB.
- JWT generowany w teście (HS256 + ten sam `JWT_SECRET`) — bez przechodzenia przez `/auth/login`.
- Pokrycie wszystkich endpointów aplikacyjnych, po kilka testów każdy, w tym dane błędne/niepełne.
- Każdy etap testu komentowany po angielsku.

## Out of Scope

- `POST /auth/login` (zależność od zewnętrznego Google `tokeninfo`) — pominięty.
- `POST /deploy` happy-path (podmienia binarkę i ubija proces) — tylko ścieżki odrzucenia (503/401/400).
- Testy frontendu.
