package pl.rkarpinski.fiszkiwbiegu.data.repository

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionRequest

class CollectionRepository(private val api: ApiClient) {
    suspend fun getAll(): Result<List<CollectionDto>> = runCatching {
        val response = api.getCollections()
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun create(name: String): Result<CollectionDto> = runCatching {
        val response = api.createCollection(CollectionRequest(name))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun rename(id: String, name: String): Result<CollectionDto> = runCatching {
        val response = api.updateCollection(id, CollectionRequest(name))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val response = api.deleteCollection(id)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }
}
