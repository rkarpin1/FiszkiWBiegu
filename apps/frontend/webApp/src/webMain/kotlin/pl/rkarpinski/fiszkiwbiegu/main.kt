// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.koin.core.context.startKoin
import pl.rkarpinski.fiszkiwbiegu.di.appModule
import pl.rkarpinski.fiszkiwbiegu.di.webModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(appModule, webModule)
    }
    ComposeViewport {
        App(
            onGoogleSignIn = { googleSignIn() },
            learningEnabled = false,
            onDownloadApk = { downloadApk() },
        )
    }
}
