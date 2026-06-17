package pl.rkarpinski.fiszkiwbiegu.screens.learning

import kotlinx.coroutines.flow.StateFlow
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

enum class LearningPhase { IDLE, SPEAKING_SOURCE, ANSWER, SPEAKING_TARGET, REPEATING }

data class LearningState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val flashcards: List<FlashcardDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: LearningPhase = LearningPhase.IDLE,
)

interface LearningController {
    val state: StateFlow<LearningState>
    fun start(collection: CollectionDto, flashcards: List<FlashcardDto>)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun stop()
}