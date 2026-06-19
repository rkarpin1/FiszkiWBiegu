---
id: back-stack-leak
title: "Przyciek back stack — powrót do nauki po wyjściu przez powiadomienie"
status: preparing
created: 2026-06-19
updated: 2026-06-19
---

## Cel

Naprawić bug: po wejściu w tryb nauki, kliknięciu powiadomienia, wyjściu przez ← (kolekcje) i kolejnym back — aplikacja wraca do Learning zamiast zamknąć się / pokazać ekran główny.

## Przyczyna główna

`MainActivity` ma domyślny `launchMode = "standard"`. Powiadomienie odpala PendingIntent, do którego system Android (12+) automatycznie dodaje `FLAG_ACTIVITY_NEW_TASK`. Przy standardowym launchMode może to stworzyć NOWE zadanie (Task) z nową instancją MainActivity, obok istniejącego zadania z pierwszą instancją. Systemowy "back" z nowego zadania przywraca stare zadanie — które nadal ma Learning na stosie Compose.
