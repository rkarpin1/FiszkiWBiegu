package pl.rkarpinski.fiszkiwbiegu.domain

import kotlin.random.Random
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

enum class Rating { DONT_KNOW, KNOW, KNOW_WELL }

data class SrsCard(
    var flashcard: FlashcardDto,
    var srsLevel: Float,
    var dueAtIndex: Int,
)

object SrsEngine {

    fun initQueue(flashcards: List<FlashcardDto>, rng: Random = Random.Default): MutableList<SrsCard> =
        flashcards
            .shuffled(rng)
            .sortedBy { it.decayLevel() + rng.nextFloat() * 0.3f }
            .mapIndexed { i, card -> SrsCard(card, card.decayLevel(), dueAtIndex = i) }
            .toMutableList()

    fun pickNext(queue: List<SrsCard>, globalIndex: Int): SrsCard =
        queue.filter { it.dueAtIndex <= globalIndex }
            .minByOrNull { it.srsLevel }
            ?: queue.minBy { it.dueAtIndex }

    fun intervalFor(level: Float, rating: Rating, rng: Random = Random.Default): Int {
        val base = when (rating) {
            Rating.DONT_KNOW -> return 2
            Rating.KNOW      -> maxOf(3, (level * 10f).toInt())
            Rating.KNOW_WELL -> maxOf(10, (level * 30f).toInt())
        }
        val jitter = maxOf(1, (base * 0.2f).toInt())
        return maxOf(1, base + rng.nextInt(-jitter, jitter + 1))
    }

    fun newLevel(current: Float, rating: Rating): Float = when (rating) {
        Rating.DONT_KNOW -> maxOf(0f, current - 0.1f)
        Rating.KNOW      -> minOf(1f, current + 0.01f)
        Rating.KNOW_WELL -> minOf(1f, current + 0.15f)
    }
}
