package pl.rkarpinski.fiszkiwbiegu

import kotlinx.coroutines.flow.StateFlow
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

enum class LearningPhase { IDLE, SPEAKING_POLISH, SPEAKING_ENGLISH }

data class LearningState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val flashcards: List<FlashcardDto> = emptyList(),
    val currentIndex: Int = 0,
    val phase: LearningPhase = LearningPhase.IDLE,
)

interface LearningController {
    val state: StateFlow<LearningState>
    fun start(flashcards: List<FlashcardDto>)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun stop()
}