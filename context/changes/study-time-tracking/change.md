---
change_id: study-time-tracking
title: Śledzenie łącznego czasu nauki kolekcji
status: preparing
created: 2026-06-19
updated: 2026-06-19
---

## Goal

Dodać pole `total_study_minutes` do kolekcji — backend je przechowuje i akumuluje po każdej sesji nauki, frontend oblicza czas aktywnej sesji i wysyła go razem z `progress`, a ekran kolekcji wyświetla sformatowaną wartość zamiast obecnego "—".

## Format wyświetlania

- `< 1440 min`: `"X min"`
- `>= 1440 min`: `"X dn Y min"` gdzie `X = total / 1440`, `Y = total % 1440`

Przykłady z PRD: `30 min`, `3 dn 23 min`, `578 dn 78 min`
