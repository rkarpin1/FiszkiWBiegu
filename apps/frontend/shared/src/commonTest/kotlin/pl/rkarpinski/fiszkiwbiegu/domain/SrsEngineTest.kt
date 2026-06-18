package pl.rkarpinski.fiszkiwbiegu.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

class SrsEngineTest {

    private val fixedNow = Instant.parse("2026-06-18T12:00:00Z")

    private fun card(srsLevel: Float = 0f, lastStudiedAt: String? = null) = FlashcardDto(
        id = "id",
        collectionId = "col",
        sourceText = "src",
        targetText = "tgt",
        position = 0,
        createdAt = "2026-01-01",
        srsLevel = srsLevel,
        lastStudiedAt = lastStudiedAt,
    )

    @Test
    fun `intervalFor DONT_KNOW always returns 2`() {
        repeat(20) {
            assertEquals(2, SrsEngine.intervalFor(0f, Rating.DONT_KNOW))
            assertEquals(2, SrsEngine.intervalFor(0.5f, Rating.DONT_KNOW))
            assertEquals(2, SrsEngine.intervalFor(1f, Rating.DONT_KNOW))
        }
    }

    @Test
    fun `intervalFor KNOW level 0 stays in jitter range around base 3`() {
        // base=3, jitter=1 → range [2, 4]
        repeat(50) {
            val interval = SrsEngine.intervalFor(0f, Rating.KNOW)
            assertTrue(interval in 2..4, "Expected 2-4, got $interval")
        }
    }

    @Test
    fun `intervalFor KNOW level 0_5 stays in jitter range around base 5`() {
        // base=max(3, 0.5*10)=5, jitter=max(1, 5*0.2)=1 → range [4, 6]
        val rng = Random(42)
        repeat(100) {
            val interval = SrsEngine.intervalFor(0.5f, Rating.KNOW, rng)
            assertTrue(interval in 4..6, "Expected 4-6, got $interval")
        }
    }

    @Test
    fun `intervalFor KNOW_WELL level 0 stays in jitter range around base 10`() {
        // base=10, jitter=max(1, 10*0.2)=2 → range [8, 12]
        repeat(50) {
            val interval = SrsEngine.intervalFor(0f, Rating.KNOW_WELL)
            assertTrue(interval in 8..12, "Expected 8-12, got $interval")
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
    fun `pickNext returns due card with lowest srsLevel`() {
        val cards = mutableListOf(
            SrsCard(card(0.8f), 0.8f, dueAtIndex = 0),
            SrsCard(card(0.2f), 0.2f, dueAtIndex = 0),
            SrsCard(card(0.5f), 0.5f, dueAtIndex = 0),
        )
        val picked = SrsEngine.pickNext(cards, globalIndex = 5)
        assertEquals(0.2f, picked.srsLevel)
    }

    @Test
    fun `pickNext falls back to earliest dueAtIndex when nothing is due`() {
        val cards = mutableListOf(
            SrsCard(card(0.1f), 0.1f, dueAtIndex = 10),
            SrsCard(card(0.9f), 0.9f, dueAtIndex = 3),
        )
        val picked = SrsEngine.pickNext(cards, globalIndex = 0)
        assertEquals(3, picked.dueAtIndex)
    }

    @Test
    fun `initQueue produces all cards`() {
        val flashcards = (1..5).map { card() }
        val queue = SrsEngine.initQueue(flashcards, Random(0))
        assertEquals(5, queue.size)
    }

    @Test
    fun `initQueue assigns ascending dueAtIndex`() {
        val flashcards = (1..5).map { card() }
        val queue = SrsEngine.initQueue(flashcards, Random(0))
        val indices = queue.map { it.dueAtIndex }
        assertEquals(indices.sorted(), indices)
    }

    @Test
    fun `decayLevel null lastStudiedAt returns level unchanged`() {
        assertEquals(0.8f, SrsEngine.decayLevel(0.8f, null, fixedNow))
    }

    @Test
    fun `decayLevel level 0 stays 0 regardless of timestamp`() {
        // 7 days before fixedNow
        assertEquals(0.0f, SrsEngine.decayLevel(0.0f, "2026-06-11T12:00:00Z", fixedNow))
    }

    @Test
    fun `decayLevel 0_8 after 7 days is approximately 0_597`() {
        // stability = 1 + 0.8 * 29 = 24.2; decay = 0.8 * e^(-7/24.2) ≈ 0.597
        val result = SrsEngine.decayLevel(0.8f, "2026-06-11T12:00:00Z", fixedNow)
        assertTrue(result in 0.587f..0.607f, "Expected ~0.597, got $result")
    }

    @Test
    fun `decayLevel 0_8 after 0 days is nearly unchanged`() {
        val result = SrsEngine.decayLevel(0.8f, "2026-06-18T12:00:00Z", fixedNow)
        assertTrue(result in 0.799f..0.801f, "Expected ~0.8, got $result")
    }

    @Test
    fun `decayLevel 1_0 after 15 days is above 0_5`() {
        // stability = 1 + 1.0 * 29 = 30; after 15 days: 1.0 * e^(-15/30) = e^(-0.5) ≈ 0.607
        val result = SrsEngine.decayLevel(1.0f, "2026-06-03T12:00:00Z", fixedNow)
        assertTrue(result > 0.5f, "Expected > 0.5 after 15 days, got $result")
    }
}
