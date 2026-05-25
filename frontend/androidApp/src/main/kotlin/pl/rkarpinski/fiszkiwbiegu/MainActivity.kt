package pl.rkarpinski.fiszkiwbiegu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.Settings
import pl.rkarpinski.fiszkiwbiegu.App
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.TokenStorage
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository

class MainActivity : ComponentActivity() {
    private val signInHelper by lazy { GoogleSignInHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = TokenStorage(Settings())
        val apiClient = ApiClient(tokenStorage)
        val authRepository = AuthRepository(apiClient, tokenStorage)
        val webClientId = "71847229905-mqalk30tubb1pstdjq73krh0ovasqf2f.apps.googleusercontent.com"

        setContent {
            App(
                authRepository = authRepository,
                onGoogleSignIn = {
                    try {
                        Result.success(signInHelper.signIn(webClientId))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                },
            )
        }
    }
}
