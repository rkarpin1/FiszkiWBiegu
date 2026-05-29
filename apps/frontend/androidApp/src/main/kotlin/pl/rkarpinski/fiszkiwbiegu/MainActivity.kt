package pl.rkarpinski.fiszkiwbiegu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val signInHelper by lazy { GoogleSignInHelper(this) }
    private val webClientId = "71847229905-mqalk30tubb1pstdjq73krh0ovasqf2f.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
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