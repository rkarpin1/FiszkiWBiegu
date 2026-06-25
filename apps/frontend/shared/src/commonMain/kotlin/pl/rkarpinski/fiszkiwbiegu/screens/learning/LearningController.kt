package pl.rkarpinski.fiszkiwbiegu.screens.learning

import kotlinx.coroutines.flow.StateFlow
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.domain.Rating

enum class LearningPhase { IDLE, SPEAKING_SOURCE, ANSWER, SPEAKING_TARGET, REPEATING }

data class LearningState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val flashcards: List<FlashcardDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: LearningPhase = LearningPhase.IDLE,
    val currentCard: FlashcardDto? = null,
    val playbackSpeed: Float = 1.0f,
    /** Bieżąca fiszka została już oceniona — blokuje przyciski „Wiem"/„Nie wiem"
     *  do czasu przejścia do kolejnej fiszki (np. gdy „Nie wiem" dogrywa do końca). */
    val isRated: Boolean = false,
)

interface LearningController {
    val state: StateFlow<LearningState>
    fun start(collection: CollectionDto, flashcards: List<FlashcardDto>)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun stop()
    fun rate(rating: Rating)
    fun setSpeed(speed: Float)
}