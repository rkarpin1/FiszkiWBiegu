package pl.rkarpinski.fiszkiwbiegu.data.repository

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardRequest
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardUpdateRequest

class FlashcardRepository(private val api: ApiClient) {
    suspend fun getAll(collectionId: String): Result<List<FlashcardDto>> = runCatching {
        val response = api.getFlashcards(collectionId)
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun create(collectionId: String, sourceText: String, targetText: String): Result<FlashcardDto> = runCatching {
        val response = api.createFlashcard(collectionId, FlashcardRequest(sourceText, targetText))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun update(id: String, sourceText: String? = null, targetText: String? = null): Result<FlashcardDto> = runCatching {
        val response = api.updateFlashcard(id, FlashcardUpdateRequest(sourceText, targetText))
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val response = api.deleteFlashcard(id)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }

    suspend fun getLearningSession(collectionId: String): Result<List<FlashcardDto>> = runCatching {
        val response = api.getLearningSession(collectionId)
        if (response.status.isSuccess()) response.body()
        else error("HTTP ${response.status.value}")
    }
}
