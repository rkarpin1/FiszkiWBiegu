package pl.rkarpinski.fiszkiwbiegu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.repository.CollectionRepository
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository

class LearningViewModel(
    private val repo: FlashcardRepository,
    private val collectionRepo: CollectionRepository,
    private val controller: LearningController,
    private val collectionId: String,
) : ViewModel() {

    val state: StateFlow<LearningState> = controller.state

    fun startSession() {
        viewModelScope.launch {
            repo.getAll(collectionId).onSuccess { flashcards ->
                controller.start(flashcards)
            }
        }
    }

    fun play() = controller.play()
    fun pause() = controller.pause()
    fun next() = controller.next()
    fun previous() = controller.previous()

    fun stop() {
        val s = controller.state.value
        if (s.flashcards.isNotEmpty()) {
            viewModelScope.launch {
                collectionRepo.markStudied(
                    collectionId,
                    cardsHeard = minOf(s.currentIndex + 1, s.flashcards.size),
                    totalCards = s.flashcards.size,
                )
            }
        }
        controller.stop()
    }
}
