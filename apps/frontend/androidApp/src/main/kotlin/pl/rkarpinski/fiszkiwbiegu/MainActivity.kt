package pl.rkarpinski.fiszkiwbiegu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val signInHelper by lazy { GoogleSignInHelper(this) }
    private val webClientId = "71847229905-mqalk30tubb1pstdjq73krh0ovasqf2f.apps.googleusercontent.com"

    private var initialCollectionJson by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initialCollectionJson = intent.getStringExtra("collection_json")
        setContent {
            App(
                onGoogleSignIn = {
                    try {
                        Result.success(signInHelper.signIn(webClientId))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                },
                initialCollectionJson = initialCollectionJson,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialCollectionJson = intent.getStringExtra("collection_json")
    }
}
