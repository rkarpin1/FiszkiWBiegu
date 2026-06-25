package pl.rkarpinski.fiszkiwbiegu.domain

import kotlin.random.Random
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

enum class Rating { DONT_KNOW, KNOW, KNOW_WELL }

data class SrsCard(
    var flashcard: FlashcardDto,
    var srsLevel: Float,
    // Wirtualny indeks pozycji w czasie sesji (mniejszy = wcześniej). Przód kolejki ma
    // zawsze 0 (po normalizacji). Może przekraczać liczbę kart — to trwała „pamięć"
    // jak daleko karta została odsunięta, przeżywająca wiele cykli.
    var index: Int = 0,
)

object SrsEngine {

    fun initQueue(flashcards: List<FlashcardDto>, rng: Random = Random.Default): MutableList<SrsCard> =
        flashcards
            .shuffled(rng)
            .sortedBy { it.decayLevel() + rng.nextFloat() * 0.3f }
            .mapIndexed { i, card -> SrsCard(card, card.decayLevel(), index = i) }
            .toMutableList()

    /**
     * O ile przesunąć indeks karty po ocenie (względem przodu = 0). Wynik to dystans w
     * „wirtualnych krokach", a nie pozycja w liście — może przekraczać jej długość, więc
     * dobrze znane karty realnie rzadziej wracają, nawet w małej talii.
     */
    fun offsetFor(level: Float, rating: Rating, rng: Random = Random.Default): Int {
        val base = when (rating) {
            // "Nie wiem": stałe +1 (bez jittera) — fiszka wraca dokładnie po jednej
            // następnej karcie, nigdy zaraz po sobie, niezależnie od stanu reszty kolejki.
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
 * Stateful wrapper around SrsEngine. Kolejka uporządkowana po [SrsCard.index] rosnąco:
 * przód (najmniejszy indeks) to następna karta. Ocena przesuwa kartę o [SrsEngine.offsetFor]
 * w wirtualnym czasie, po czym indeksy są normalizowane tak, by przód miał 0.
 *
 * Własności:
 *  - „Nie wiem" (+1) wraca dokładnie po jednej karcie (reguła remisu: karta świeżo
 *    przeniesiona na dany indeks ląduje ZA tymi, które już tam są).
 *  - Brak zagłodzenia: indeks karty rośnie tylko gdy ją oceniamy; w pozostałych turach
 *    maleje przez normalizację, więc każda karta monotonicznie zbliża się do przodu.
 *  - Różnicowanie częstotliwości działa też w małych taliach (indeks nie jest przycinany
 *    do długości listy).
 */
class SrsQueue(private val rng: Random = Random.Default) {

    private val _cards = mutableListOf<SrsCard>()
    val cards: List<SrsCard> get() = _cards

    fun init(flashcards: List<FlashcardDto>) {
        _cards.clear()
        _cards.addAll(SrsEngine.initQueue(flashcards, rng))
        normalize()
    }

    fun clear() {
        _cards.clear()
    }

    fun isEmpty() = _cards.isEmpty()
    fun isNotEmpty() = _cards.isNotEmpty()

    // Peek przodu (najmniejszy indeks) — karta zostaje na liście (kompletna dla UI) aż do rate().
    fun pickNext(): SrsCard = _cards.first()

    fun rate(card: SrsCard, rating: Rating) {
        card.srsLevel = SrsEngine.newLevel(card.srsLevel, rating)
        val pos = _cards.indexOfFirst { it === card }
        if (pos < 0) return
        _cards.removeAt(pos)

        if (rating == Rating.DONT_KNOW) {
            // „Nie wiem": pozycyjnie zaraz ZA następną kartą (lista[0]) → odstęp dokładnie 1,
            // niezależnie od gęstości osi indeksów (samo +1 nie wystarcza, gdy następna
            // karta jest daleko w czasie wirtualnym).
            if (_cards.isEmpty()) {
                card.index = 0
                _cards.add(card)
            } else {
                card.index = _cards.first().index            // remis z następną kartą
                _cards.add(1.coerceAtMost(_cards.size), card) // pozycja 1 = za jedną kartą
            }
        } else {
            // Indeks docelowy = offset, ale NIGDY mniejszy niż indeks następnej karty —
            // inaczej przy rozrzedzonej osi (wszystkie pozostałe karty daleko) karta wróciłaby
            // na pozycję 0 i zagrała od razu drugi raz (dubel). maxOf gwarantuje odstęp >= 1.
            val target = card.index + SrsEngine.offsetFor(card.srsLevel, rating, rng)
            card.index = maxOf(target, _cards.firstOrNull()?.index ?: 0)
            // Wstaw ZA wszystkie karty o indeksie <= nowy (remis: świeżo przesunięta idzie dalej).
            val insertAt = _cards.indexOfFirst { it.index > card.index }
                .let { if (it < 0) _cards.size else it }
            _cards.add(insertAt, card)
        }

        normalize()
    }

    // Przesuń całą oś czasu tak, by przód miał indeks 0 (utrzymuje małe liczby i sens "+offset").
    private fun normalize() {
        val min = _cards.firstOrNull()?.index ?: return
        if (min == 0) return
        for (c in _cards) c.index -= min
    }
}
