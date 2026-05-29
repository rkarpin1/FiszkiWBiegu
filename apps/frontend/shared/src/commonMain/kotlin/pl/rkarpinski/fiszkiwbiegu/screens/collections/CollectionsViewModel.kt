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
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
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

    fun showAddDialog() = _uiState.update { it.copy(showAddDialog = true) }

    fun hideAddDialog() = _uiState.update { it.copy(showAddDialog = false) }

    fun createCollection(dto: CollectionDto) {
        viewModelScope.launch {
            repo.create(dto).fold(
                onSuccess = { loadCollections() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
            _uiState.update { it.copy(showAddDialog = false) }
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

    fun updateCollection(dto: CollectionDto) {
        viewModelScope.launch {
            repo.rename(dto).fold(
                onSuccess = { loadCollections() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
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
}
