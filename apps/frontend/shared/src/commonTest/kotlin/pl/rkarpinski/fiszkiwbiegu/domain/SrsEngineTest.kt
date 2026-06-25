package pl.rkarpinski.fiszkiwbiegu.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

class SrsEngineTest {

    private fun card(srsLevel: Float = 0f, lastStudiedAt: String? = null, id: String = "id") = FlashcardDto(
        id = id,
        collectionId = "col",
        sourceText = "src",
        targetText = "tgt",
        position = 0,
        createdAt = "2026-01-01",
        srsLevel = srsLevel,
        lastStudiedAt = lastStudiedAt,
    )

    @Test
    fun `offsetFor DONT_KNOW always returns 1`() {
        repeat(20) {
            assertEquals(1, SrsEngine.offsetFor(0f, Rating.DONT_KNOW))
            assertEquals(1, SrsEngine.offsetFor(0.5f, Rating.DONT_KNOW))
            assertEquals(1, SrsEngine.offsetFor(1f, Rating.DONT_KNOW))
        }
    }

    @Test
    fun `offsetFor KNOW level 0 stays in jitter range around base 3`() {
        // base=3, jitter=1 → range [2, 4]
        repeat(50) {
            val offset = SrsEngine.offsetFor(0f, Rating.KNOW)
            assertTrue(offset in 2..4, "Expected 2-4, got $offset")
        }
    }

    @Test
    fun `offsetFor KNOW level 0_5 stays in jitter range around base 5`() {
        // base=max(3, 0.5*10)=5, jitter=max(1, 5*0.2)=1 → range [4, 6]
        val rng = Random(42)
        repeat(100) {
            val offset = SrsEngine.offsetFor(0.5f, Rating.KNOW, rng)
            assertTrue(offset in 4..6, "Expected 4-6, got $offset")
        }
    }

    @Test
    fun `offsetFor KNOW_WELL level 0 stays in jitter range around base 10`() {
        // base=10, jitter=max(1, 10*0.2)=2 → range [8, 12]
        repeat(50) {
            val offset = SrsEngine.offsetFor(0f, Rating.KNOW_WELL)
            assertTrue(offset in 8..12, "Expected 8-12, got $offset")
        }
    }

    @Test
    fun `newLevel DONT_KNOW does not go below 0`() {
        assertEquals(0f, SrsEngine.newLevel(0f, Rating.DONT_KNOW))
        assertEquals(0f, SrsEngine.newLevel(0.05f, Rating.DONT_KNOW))
    }

    @Test
    fun `newLevel KNOW_WELL does not exceed 1`() {
        assertEquals(1f, SrsEngine.newLevel(1f, Rating.KNOW_WELL))
        assertEquals(1f, SrsEngine.newLevel(0.95f, Rating.KNOW_WELL))
    }

    @Test
    fun `newLevel KNOW increases level`() {
        val before = 0.3f
        val after = SrsEngine.newLevel(before, Rating.KNOW)
        assertTrue(after > before)
    }

    @Test
    fun `newLevel DONT_KNOW decreases level`() {
        val before = 0.5f
        val after = SrsEngine.newLevel(before, Rating.DONT_KNOW)
        assertTrue(after < before)
    }

    @Test
    fun `DONT_KNOW does not replay the same card immediately`() {
        // Regresja na regułę "fiszka nie odtwarza się zaraz po sobie": po ocenie
        // DONT_KNOW kolejne losowanie musi zwrócić INNĄ fiszkę (gdy w kolejce jest >= 2).
        val flashcards = (1..3).map { card(id = "c$it") }
        repeat(50) { seed ->
            val queue = SrsQueue(Random(seed.toLong()))
            queue.init(flashcards)
            val first = queue.pickNext()
            queue.rate(first, Rating.DONT_KNOW)
            val second = queue.pickNext()
            assertTrue(
                first.flashcard.id != second.flashcard.id,
                "DONT_KNOW replayed ${first.flashcard.id} immediately (seed=$seed)",
            )
        }
    }

    @Test
    fun `DONT_KNOW always returns the card after exactly one other card`() {
        // Regresja na zgłoszony błąd: po "Nie wiem" fiszka musi wrócić DOKŁADNIE po jednej
        // następnej karcie (odstęp 1), niezależnie od stanu reszty kolejki. Najpierw
        // mieszamy kolejkę kilkoma ocenami KNOW/KNOW_WELL, potem oceniamy "Nie wiem".
        val flashcards = (1..8).map { card(id = "c$it") }
        repeat(50) { seed ->
            val queue = SrsQueue(Random(seed.toLong()))
            queue.init(flashcards)

            // Zróżnicuj kolejność, by powstała "zaległość" jak w realnej sesji.
            repeat(5) { i ->
                val c = queue.pickNext()
                queue.rate(c, if (i % 2 == 0) Rating.KNOW else Rating.KNOW_WELL)
            }

            val x = queue.pickNext()
            queue.rate(x, Rating.DONT_KNOW)

            val next = queue.pickNext()
            assertTrue(
                next.flashcard.id != x.flashcard.id,
                "DONT_KNOW replayed ${x.flashcard.id} immediately (seed=$seed)",
            )
            queue.rate(next, Rating.KNOW)

            val afterNext = queue.pickNext()
            assertEquals(
                x.flashcard.id,
                afterNext.flashcard.id,
                "DONT_KNOW card did not return after exactly one card (seed=$seed)",
            )
        }
    }

    @Test
    fun `KNOW_WELL card plays less often than KNOW cards even in a tiny deck`() {
        // Sedno modelu indeksowego: dobrze znana karta realnie rzadziej wraca, nawet gdy
        // talia jest mała (indeks nie jest przycinany do długości listy — czego nie dawała
        // czysta kolejka pozycyjna, która w 3 kartach degenerowała się do round-robin).
        val flashcards = (1..3).map { card(id = "c$it") }
        repeat(20) { seed ->
            val queue = SrsQueue(Random(seed.toLong()))
            queue.init(flashcards)
            val counts = mutableMapOf("c1" to 0, "c2" to 0, "c3" to 0)
            repeat(60) {
                val c = queue.pickNext()
                counts[c.flashcard.id] = counts.getValue(c.flashcard.id) + 1
                // c1 zawsze "Wiem dobrze", reszta "Wiem".
                queue.rate(c, if (c.flashcard.id == "c1") Rating.KNOW_WELL else Rating.KNOW)
            }
            assertTrue(
                counts.getValue("c1") < counts.getValue("c2") &&
                    counts.getValue("c1") < counts.getValue("c3"),
                "KNOW_WELL nie obniżyło częstotliwości: $counts (seed=$seed)",
            )
        }
    }

    @Test
    fun `a KNOW_WELL pushed card always returns within a bounded gap (no starvation)`() {
        // Karta odsunięta przez "Wiem dobrze" musi wrócić — indeks rośnie tylko gdy kartę
        // oceniamy, a w pozostałych turach maleje przez normalizację, więc każda karta
        // monotonicznie zbliża się do przodu. Brak zagłodzenia, ale i nie wraca natychmiast.
        val flashcards = (1..5).map { card(id = "c$it") }
        repeat(20) { seed ->
            val queue = SrsQueue(Random(seed.toLong()))
            queue.init(flashcards)
            val target = queue.pickNext().flashcard.id
            queue.rate(queue.pickNext(), Rating.KNOW_WELL) // odsuń kartę daleko

            var gap = 0
            while (queue.pickNext().flashcard.id != target) {
                queue.rate(queue.pickNext(), Rating.KNOW)
                gap++
                assertTrue(gap < 40, "Zagłodzenie: $target nie wróciła (seed=$seed)")
            }
            assertTrue(gap >= 1, "KNOW_WELL nie odsunęło karty (seed=$seed)")
        }
    }

    @Test
    fun `never plays the same card twice in a row under any KNOW mix`() {
        // Regresja na "dubla": gdy oś indeksów jest rozrzedzona (część kart odsunięta daleko
        // przez KNOW_WELL), oceniana karta nie może wrócić na pozycję 0 i zagrać od razu znowu.
        val flashcards = (1..4).map { card(id = "c$it") }
        repeat(30) { seed ->
            val rng = Random(seed.toLong())
            val queue = SrsQueue(Random(seed.toLong() xor 0x5DEECE6DL))
            queue.init(flashcards)
            var prev: String? = null
            repeat(200) {
                val c = queue.pickNext()
                assertTrue(
                    c.flashcard.id != prev,
                    "Dubel: ${c.flashcard.id} zagrana dwa razy z rzędu (seed=$seed)",
                )
                prev = c.flashcard.id
                // Mocno mieszamy oceny, w tym dużo KNOW_WELL, by tworzyć rozrzedzone osie.
                val rating = when (rng.nextInt(3)) {
                    0 -> Rating.KNOW_WELL
                    1 -> Rating.KNOW
                    else -> Rating.DONT_KNOW
                }
                queue.rate(c, rating)
            }
        }
    }

    @Test
    fun `initQueue produces all cards`() {
        val flashcards = (1..5).map { card() }
        val queue = SrsEngine.initQueue(flashcards, Random(0))
        assertEquals(5, queue.size)
    }
}
