package pl.karpinski.fiszkiwbiegu.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CollectionRequest(
    val name: String,
)

@Serializable
data class FlashcardDto(
    val id: String,
    @SerialName("collection_id") val collectionId: String,
    @SerialName("polish_text") val polishText: String,
    @SerialName("english_text") val englishText: String,
    val position: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FlashcardRequest(
    @SerialName("polish_text") val polishText: String,
    @SerialName("english_text") val englishText: String,
)

@Serializable
data class FlashcardUpdateRequest(
    @SerialName("polish_text") val polishText: String? = null,
    @SerialName("english_text") val englishText: String? = null,
)

@Serializable
data class LoginRequest(
    @SerialName("id_token") val idToken: String,
)

@Serializable
data class LoginResponse(
    val token: String,
)
