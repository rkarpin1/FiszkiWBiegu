package pl.rkarpinski.fiszkiwbiegu.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rkarpinski.fiszkiwbiegu.data.repository.ProfileRepository

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val streakDays: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getMe().fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            displayName = user.displayName
                                ?: user.email.substringBefore('@'),
                            email = user.email,
                            streakDays = user.streakDays,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        }
    }
}
