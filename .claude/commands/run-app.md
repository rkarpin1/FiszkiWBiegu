Sprawdź aktualną gałąź git poleceniem `git branch --show-current`.

Jeśli gałąź to NIE `MVP`, wypisz dokładnie: "Nie jesteś w MVP" i zakończ.

Jeśli gałąź to `MVP`, wykonaj kolejno:

1. Sprawdź `git status --porcelain`. Jeśli są niezatwierdzone zmiany, zapytaj użytkownika o wiadomość commitu i zrób commit (użyj heredoc, dodaj trailer `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`). Jeśli nie ma zmian, pomiń commit.

2. `git push origin MVP`

3. `git checkout master`

4. `git merge MVP --no-ff` z wiadomością opisującą co jest mergowane (użyj `git log master..MVP --oneline` żeby zobaczyć co wchodzi).

5. `git push origin master`

6. `git checkout MVP`

Po zakończeniu wypisz krótkie podsumowanie: SHA merge commitu i ile commitów trafiło do mastera.
