package pl.rkarpinski.fiszkiwbiegu.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.repository.CollectionRepository

data class CollectionsUiState(
    val collections: List<CollectionDto> = emptyList(),
    val lastStudiedCollection: CollectionDto? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val pendingDeleteId: String? = null,
)

class CollectionsViewModel(private val repo: CollectionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        loadCollections()
    }

    fun loadCollections() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getAll().fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            collections = list,
                            lastStudiedCollection = list.filter { c -> c.lastStudied != null }
                                .maxByOrNull { c -> c.lastStudied!! },
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

    fun createCollection(dto: CollectionDto, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            repo.create(dto).fold(
                onSuccess = { loadCollections(); onSuccess() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun requestDelete(id: String) = _uiState.update { it.copy(pendingDeleteId = id) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDeleteId = null) }

    fun confirmDelete() {
        val id = _uiState.value.pendingDeleteId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingDeleteId = null, isLoading = true) }
            repo.delete(id).fold(
                onSuccess = { loadCollections() },
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

    fun updateCollection(dto: CollectionDto, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            repo.rename(dto).fold(
                onSuccess = { loadCollections(); onSuccess() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun deleteCollection(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repo.delete(id).fold(
                onSuccess = { loadCollections(); onSuccess() },
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
