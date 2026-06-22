package pl.rkarpinski.fiszkiwbiegu.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository

data class FlashcardsUiState(
    val flashcards: List<FlashcardDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingDeleteId: String? = null,
    val isTranslating: Boolean = false,
    val translationError: String? = null,
)

class FlashcardsViewModel(
    private val repo: FlashcardRepository,
    private val collectionId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardsUiState())
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()

    init {
        loadFlashcards()
    }

    fun loadFlashcards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getAll(collectionId).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            flashcards = list,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                },
            )
        }
    }

    fun requestDelete(id: String) = _uiState.update { it.copy(pendingDeleteId = id) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDeleteId = null) }

    fun confirmDelete() {
        val id = _uiState.value.pendingDeleteId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingDeleteId = null, isLoading = true) }
            repo.delete(id).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                },
            )
        }
    }

    fun createCard(flashcard: FlashcardDto) {
        viewModelScope.launch {
            repo.create(collectionId, flashcard.sourceText, flashcard.targetText).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun updateCard(flashcard: FlashcardDto) {
        viewModelScope.launch {
            repo.update(flashcard.id, flashcard.sourceText, flashcard.targetText).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    /**
     * Translate [text] from [sourceLanguage] to [targetLanguage] and hand the
     * result back via [onResult] so the form can place it in the right field.
     * The direction is decided by the caller (which field is empty).
     */
    fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, translationError = null) }
            repo.translate(text, sourceLanguage, targetLanguage).fold(
                onSuccess = { translated ->
                    _uiState.update { it.copy(isTranslating = false) }
                    onResult(translated)
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            translationError = "Nie udało się przetłumaczyć",
                        )
                    }
                },
            )
        }
    }

    fun deleteFlashcard(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repo.delete(id).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                },
            )
        }
    }
}
