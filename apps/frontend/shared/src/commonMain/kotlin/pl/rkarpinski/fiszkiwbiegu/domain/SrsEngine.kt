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

    fun pickNext(queue: List<SrsCard>, globalIndex: Int, lastPickedId: String? = null): SrsCard {
        val due = queue.filter { it.dueAtIndex <= globalIndex }
        val order = compareBy<SrsCard>({ it.dueAtIndex }, { it.srsLevel })
        val preferredDue = due.filter { it.flashcard.id != lastPickedId }
        if (preferredDue.isNotEmpty()) return preferredDue.minWith(order)
        if (due.isNotEmpty()) return due.minWith(order)
        return queue.filter { it.flashcard.id != lastPickedId }.minByOrNull { it.dueAtIndex }
            ?: queue.minBy { it.dueAtIndex }
    }

    fun intervalFor(level: Float, rating: Rating, rng: Random = Random.Default): Int {
        val base = when (rating) {
            // "Nie wiem": stały interval 1 (bez jittera) — fiszka wraca za następną kartą,
            // nigdy zaraz po sobie. dueAtIndex = globalIndex + 1, a globalIndex jest już
            // zinkrementowany w pickNext(), więc najbliższe losowanie wybierze inną kartę.
            Rating.DONT_KNOW -> return 1
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

/**
 * Stateful wrapper around SrsEngine that manages the card queue, global index,
 * and last-picked tracking. All scheduling decisions (intervals, gap logic) live here.
 */
class SrsQueue(private val rng: Random = Random.Default) {

    private val _cards = mutableListOf<SrsCard>()
    val cards: List<SrsCard> get() = _cards

    private var globalIndex = 0
    private var lastPickedId: String? = null

    fun init(flashcards: List<FlashcardDto>) {
        _cards.clear()
        _cards.addAll(SrsEngine.initQueue(flashcards, rng))
        globalIndex = 0
        lastPickedId = null
    }

    fun clear() {
        _cards.clear()
        globalIndex = 0
        lastPickedId = null
    }

    fun isEmpty() = _cards.isEmpty()
    fun isNotEmpty() = _cards.isNotEmpty()

    fun pickNext(): SrsCard {
        val card = SrsEngine.pickNext(_cards, globalIndex, lastPickedId)
        globalIndex++
        lastPickedId = card.flashcard.id
        return card
    }

    fun rate(card: SrsCard, rating: Rating) {
        card.srsLevel = SrsEngine.newLevel(card.srsLevel, rating)
        card.dueAtIndex = globalIndex + SrsEngine.intervalFor(card.srsLevel, rating, rng)
    }
}
