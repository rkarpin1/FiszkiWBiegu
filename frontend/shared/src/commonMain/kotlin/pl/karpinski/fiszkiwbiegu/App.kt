// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

package pl.karpinski.fiszkiwbiegu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import pl.karpinski.fiszkiwbiegu.data.repository.AuthRepository

@Composable
fun App(
    authRepository: AuthRepository,
    onGoogleSignIn: suspend () -> Result<String>,
) {
    val scope = rememberCoroutineScope()
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        if (isLoggedIn) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Zalogowano! Ekrany kolekcji w budowie.")
            }
        } else {
            LoginScreen(
                isLoading = isLoading,
                error = error,
                onSignInClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        val idTokenResult = onGoogleSignIn()
                        idTokenResult.fold(
                            onSuccess = { idToken ->
                                authRepository.loginWithGoogle(idToken).fold(
                                    onSuccess = { isLoggedIn = true },
                                    onFailure = { e -> error = e.message ?: "Błąd logowania" },
                                )
                            },
                            onFailure = { e ->
                                error = e.message ?: "Błąd Google Sign-In"
                            },
                        )
                        isLoading = false
                    }
                },
            )
        }
    }
}
