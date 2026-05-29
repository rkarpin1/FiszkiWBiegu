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

    suspend fun create(dto: CollectionDto): Result<CollectionDto> = runCatching {
        val response = api.createCollection(CollectionRequest(dto.name, dto.description, dto.sourceLanguage, dto.targetLanguage))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun rename(dto: CollectionDto): Result<CollectionDto> = runCatching {
        val response = api.updateCollection(dto.id, CollectionRequest(dto.name, dto.description, dto.sourceLanguage, dto.targetLanguage))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val response = api.deleteCollection(id)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }

    suspend fun markStudied(id: String, cardsHeard: Int, totalCards: Int): Result<Unit> = runCatching {
        val response = api.patchLearningComplete(id, cardsHeard, totalCards)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }
}
