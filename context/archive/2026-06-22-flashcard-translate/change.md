---
change_id: flashcard-translate
title: Przycisk „Przetłumacz" w oknie definicji fiszki (darmowe API tłumaczeń przez backend)
status: archived
archived_at: 2026-06-23T12:57:22Z
created: 2026-06-22
updated: 2026-06-23
---

## Goal

Podłączyć istniejący (obecnie zablokowany) przycisk „Przetłumacz" w oknie definicji fiszki (`CardFormScreen.kt`) do darmowego serwisu tłumaczeń. Serwis ma być wywoływany jako proxy przez backend Rust/Actix-web (zgodnie z regułą architektoniczną z `lessons.md`), kompatybilny z Androidem, iOS i Web (kod współdzielony KMP). Po naciśnięciu przycisku tekst tłumaczony jest wg reguł kierunku zależnych od wypełnienia pól Source/Target.

## Scope

- Nowy endpoint `POST /translate` w backendzie (proxy do zewnętrznego API z kluczem, np. Azure Translator F0).
- Metoda klienta + repozytorium + akcja ViewModel + podłączenie przycisku w UI z regułami kierunku.
- Wykorzystanie języków kolekcji (`source_language`/`target_language`) zamiast zakodowanych „POLSKI"/„ANGIELSKI".

## Out of Scope

- Self-hosting LibreTranslate (odrzucone — wybrano darmowy tier z kluczem).
- Bezpośrednie wywołanie API z klienta (odrzucone — łamie regułę architektoniczną).
- Tłumaczenie masowe / batch wielu fiszek.

## Decisions

- **Architektura**: proxy przez backend Rust (wybór użytkownika; zgodne z `lessons.md`).
- **Typ serwisu**: darmowy tier z kluczem API (wybór użytkownika). Rekomendacja badania: Azure Translator F0; fallback: Google Cloud Translation v2.
