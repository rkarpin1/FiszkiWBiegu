-- progress przestaje być przechowywaną kolumną — backend liczy go w locie
-- jako średni decayLevel fiszek (ta sama formuła co klient: exp-decay od
-- last_studied_at), analogicznie do flashcard_count.
ALTER TABLE collections DROP COLUMN progress;
