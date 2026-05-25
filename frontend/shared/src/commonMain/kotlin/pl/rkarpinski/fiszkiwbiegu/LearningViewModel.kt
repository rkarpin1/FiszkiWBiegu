package pl.rkarpinski.fiszkiwbiegu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository

class LearningViewModel(
    private val repo: FlashcardRepository,
    private val controller: LearningController,
    private val collectionId: String,
) : ViewModel() {

    val state: StateFlow<LearningState> = controller.state

    init {
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
    fun stop() = controller.stop()
}