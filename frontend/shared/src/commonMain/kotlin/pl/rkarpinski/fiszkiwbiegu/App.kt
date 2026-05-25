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
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository

@Composable
fun App(onGoogleSignIn: suspend () -> Result<String>) {
    val authRepository: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        if (isLoggedIn) {
            CollectionsScreen(onCollectionClick = { /* TODO: ekran fiszek */ })
        } else {
            LoginScreen(
                isLoading = isLoading,
                error = error,
                onSignInClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        onGoogleSignIn().fold(
                            onSuccess = { idToken ->
                                authRepository.loginWithGoogle(idToken).fold(
                                    onSuccess = { isLoggedIn = true },
                                    onFailure = { e -> error = e.message ?: "Błąd logowania" },
                                )
                            },
                            onFailure = { e -> error = e.message ?: "Błąd Google Sign-In" },
                        )
                        isLoading = false
                    }
                },
            )
        }
    }
}
