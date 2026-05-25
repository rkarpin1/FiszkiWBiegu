package pl.rkarpinski.fiszkiwbiegu.data.api

import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings) {
    fun getToken(): String? = settings.getStringOrNull(KEY_TOKEN)
    fun saveToken(token: String) = settings.putString(KEY_TOKEN, token)
    fun clearToken() = settings.remove(KEY_TOKEN)

    private companion object {
        const val KEY_TOKEN = "auth_token"
    }
}
