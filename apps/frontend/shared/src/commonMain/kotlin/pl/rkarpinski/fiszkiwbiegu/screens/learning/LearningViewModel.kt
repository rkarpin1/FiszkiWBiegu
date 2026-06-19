package pl.rkarpinski.fiszkiwbiegu.screens.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.repository.CollectionRepository
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository
import pl.rkarpinski.fiszkiwbiegu.domain.Rating

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
                if (flashcards.isNotEmpty()) {
                    collectionRepo.getAll().onSuccess { collections ->
                        val collection = collections.find { it.id == collectionId }
                        if (collection != null) {
                            controller.start(collection, flashcards)
                        }
                    }
                }
            }
        }
    }

    fun play() = controller.play()
    fun pause() = controller.pause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun rate(rating: Rating) = controller.rate(rating)

    fun setSpeed(speed: Float) = controller.setSpeed(speed)

    fun stop() {
        val s = controller.state.value
        if (s.flashcards.isNotEmpty()) {
            val progress = s.flashcards.map { it.decayLevel() }.average().toFloat()
            viewModelScope.launch {
                collectionRepo.markStudied(collectionId, progress)
            }
        }
        controller.stop()
    }
}
