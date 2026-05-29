package pl.rkarpinski.fiszkiwbiegu.data.repository

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.UserDto

class ProfileRepository(private val api: ApiClient) {
    suspend fun getMe(): Result<UserDto> = runCatching {
        val response = api.getMe()
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }
}
