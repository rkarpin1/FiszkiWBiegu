package pl.karpinski.fiszkiwbiegu.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val API_BASE_URL = "https://fiszki-w-biegu-api.onrender.com"

class ApiClient(private val tokenStorage: TokenStorage) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getCollections(): HttpResponse =
        client.get("$API_BASE_URL/collections") { bearerAuth(requireToken()) }

    suspend fun createCollection(request: CollectionRequest): HttpResponse =
        client.post("$API_BASE_URL/collections") {
            bearerAuth(requireToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateCollection(id: String, request: CollectionRequest): HttpResponse =
        client.put("$API_BASE_URL/collections/$id") {
            bearerAuth(requireToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteCollection(id: String): HttpResponse =
        client.delete("$API_BASE_URL/collections/$id") { bearerAuth(requireToken()) }

    suspend fun getFlashcards(collectionId: String): HttpResponse =
        client.get("$API_BASE_URL/collections/$collectionId/flashcards") { bearerAuth(requireToken()) }

    suspend fun createFlashcard(collectionId: String, request: FlashcardRequest): HttpResponse =
        client.post("$API_BASE_URL/collections/$collectionId/flashcards") {
            bearerAuth(requireToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateFlashcard(id: String, request: FlashcardUpdateRequest): HttpResponse =
        client.put("$API_BASE_URL/flashcards/$id") {
            bearerAuth(requireToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteFlashcard(id: String): HttpResponse =
        client.delete("$API_BASE_URL/flashcards/$id") { bearerAuth(requireToken()) }

    suspend fun getLearningSession(collectionId: String): HttpResponse =
        client.get("$API_BASE_URL/collections/$collectionId/learning") { bearerAuth(requireToken()) }

    suspend fun login(googleIdToken: String): HttpResponse =
        client.post("$API_BASE_URL/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken))
        }

    private fun requireToken(): String =
        tokenStorage.getToken() ?: throw IllegalStateException("Not authenticated")
}
