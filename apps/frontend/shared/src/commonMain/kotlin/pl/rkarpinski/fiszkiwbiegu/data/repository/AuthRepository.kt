package pl.rkarpinski.fiszkiwbiegu.data.repository

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.LoginResponse
import pl.rkarpinski.fiszkiwbiegu.data.api.TokenStorage

class AuthRepository(
    private val api: ApiClient,
    private val tokenStorage: TokenStorage,
) {
    suspend fun loginWithGoogle(googleIdToken: String): Result<Unit> = runCatching {
        val response = api.login(googleIdToken)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val loginResponse: LoginResponse = response.body()
        tokenStorage.saveToken(loginResponse.token)
    }

    fun isLoggedIn(): Boolean = tokenStorage.getToken() != null

    fun logout() = tokenStorage.clearToken()
}
