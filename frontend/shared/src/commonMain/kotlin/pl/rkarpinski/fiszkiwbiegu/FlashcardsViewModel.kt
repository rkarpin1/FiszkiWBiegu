package pl.rkarpinski.fiszkiwbiegu

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
    val showFormDialog: Boolean = false,
    val editingFlashcard: FlashcardDto? = null,
    val pendingDeleteId: String? = null,
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
                onSuccess = { list -> _uiState.update { it.copy(flashcards = list, isLoading = false) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun showAddDialog() = _uiState.update { it.copy(showFormDialog = true, editingFlashcard = null) }

    fun showEditDialog(flashcard: FlashcardDto) = _uiState.update { it.copy(showFormDialog = true, editingFlashcard = flashcard) }

    fun hideFormDialog() = _uiState.update { it.copy(showFormDialog = false, editingFlashcard = null) }

    fun saveFlashcard(polishText: String, englishText: String) {
        viewModelScope.launch {
            val editing = _uiState.value.editingFlashcard
            _uiState.update { it.copy(showFormDialog = false, editingFlashcard = null) }
            if (editing == null) {
                repo.create(collectionId, polishText, englishText).fold(
                    onSuccess = { loadFlashcards() },
                    onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
                )
            } else {
                repo.update(editing.id, polishText, englishText).fold(
                    onSuccess = { loadFlashcards() },
                    onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
                )
            }
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
                onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun createCard(polishText: String, englishText: String) {
        viewModelScope.launch {
            repo.create(collectionId, polishText, englishText).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun updateCard(id: String, polishText: String, englishText: String) {
        viewModelScope.launch {
            repo.update(id, polishText, englishText).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun deleteFlashcard(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repo.delete(id).fold(
                onSuccess = { loadFlashcards() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }
}