package pl.rkarpinski.fiszkiwbiegu.data.repository

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionRequest

class CollectionRepository(private val api: ApiClient) {

    private val _studyCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val studyCompleted = _studyCompleted.asSharedFlow()
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

    suspend fun markStudied(id: String, sessionMinutes: Int): Result<Unit> = runCatching {
        val response = api.patchLearningComplete(id, sessionMinutes)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        _studyCompleted.emit(Unit)
    }
}
