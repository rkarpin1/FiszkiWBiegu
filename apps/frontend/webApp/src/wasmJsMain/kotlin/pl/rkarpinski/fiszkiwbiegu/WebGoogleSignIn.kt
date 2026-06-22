@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.rkarpinski.fiszkiwbiegu

import kotlinx.coroutines.delay

private fun jsReset(): JsAny? = js("(window._googleToken = null, window._googleSignInError = null, null)")
private fun jsTrigger(): JsAny? = js("(window._triggerGoogleSignIn ? window._triggerGoogleSignIn() : null)")
private fun jsToken(): JsAny? = js("window._googleToken || null")
private fun jsError(): JsAny? = js("window._googleSignInError || null")
private fun jsClearToken(): JsAny? = js("(window._googleToken = null)")
private fun jsClearError(): JsAny? = js("(window._googleSignInError = null)")
private fun jsCancel(): JsAny? = js("(window._cancelGoogleSignIn ? window._cancelGoogleSignIn() : null)")

actual suspend fun googleSignIn(): Result<String> = runCatching {
    jsReset()
    jsTrigger()

    try {
        var elapsed = 0L
        while (elapsed < 120_000L) {
            delay(200)
            elapsed += 200

            val token = jsToken()
            if (token != null) {
                jsClearToken()
                return@runCatching (token as JsString).toString()
            }

            val error = jsError()
            if (error != null) {
                jsClearError()
                throw Exception((error as JsString).toString())
            }
        }

        throw Exception("Google Sign-In — upłynął limit czasu (2 min)")
    } finally {
        jsCancel()
    }
}
