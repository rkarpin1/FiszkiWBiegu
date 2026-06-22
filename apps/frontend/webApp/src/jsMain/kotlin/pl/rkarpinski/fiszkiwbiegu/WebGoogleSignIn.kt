package pl.rkarpinski.fiszkiwbiegu

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun googleSignIn(): Result<String> = runCatching {
    suspendCancellableCoroutine { cont ->
        val fn: dynamic = js("window._initiateGoogleSignIn")
        if (fn == null) {
            cont.resumeWithException(Exception("Google Sign-In not configured"))
            return@suspendCancellableCoroutine
        }
        fn(
            { token: String -> if (cont.isActive) cont.resume(token) },
            { error: String -> if (cont.isActive) cont.resumeWithException(Exception(error)) }
        )
    }
}
