// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository

private sealed interface Destination {
    data object Login : Destination
    data object Collections : Destination
    data class Flashcards(val collection: CollectionDto) : Destination
    data class Learning(val collection: CollectionDto) : Destination
}

@Composable
fun App(onGoogleSignIn: suspend () -> Result<String>) {
    val authRepository: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()
    val initial: Destination = if (authRepository.isLoggedIn()) Destination.Collections else Destination.Login
    var destination by remember { mutableStateOf<Destination>(initial) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    MaterialTheme {
        when (val dest = destination) {
            Destination.Login -> LoginScreen(
                isLoading = isLoggingIn,
                error = loginError,
                onSignInClick = {
                    isLoggingIn = true
                    loginError = null
                    scope.launch {
                        onGoogleSignIn().fold(
                            onSuccess = { idToken ->
                                authRepository.loginWithGoogle(idToken).fold(
                                    onSuccess = { destination = Destination.Collections },
                                    onFailure = { e -> loginError = e.message ?: "Błąd logowania" },
                                )
                            },
                            onFailure = { e -> loginError = e.message ?: "Błąd Google Sign-In" },
                        )
                        isLoggingIn = false
                    }
                },
            )
            Destination.Collections -> CollectionsScreen(
                onCollectionClick = { destination = Destination.Flashcards(it) },
            )
            is Destination.Flashcards -> FlashcardsScreen(
                collection = dest.collection,
                onBack = { destination = Destination.Collections },
                onStartLearning = { destination = Destination.Learning(dest.collection) },
            )
            is Destination.Learning -> LearningScreen(
                collection = dest.collection,
                onBack = { destination = Destination.Collections },
            )
        }
    }
}