package pl.rkarpinski.fiszkiwbiegu.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectionDto(
    val id: String,

    @SerialName("user_id")
    val userId: String,

    val name: String,
    val description: String,

    @SerialName("source_language")
    val sourceLanguage: String,

    @SerialName("target_language")
    val targetLanguage: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("last_studied")
    val lastStudied: String? = null,

    val progress: Float = 0f,
    @SerialName("flashcard_count")

    val flashcardCount: Int = 0,
)

@Serializable
data class CollectionRequest(
    val name: String,
    val description: String,

    @SerialName("source_language")
    val sourceLanguage: String,

    @SerialName("target_language")
    val targetLanguage: String,
)

@Serializable
data class FlashcardDto(
    val id: String,
    @SerialName("collection_id")
    val collectionId: String,

    @SerialName("source_text")
    val sourceText: String,

    @SerialName("target_text")
    val targetText: String,

    val position: Int,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("srs_level")
    val srsLevel: Float = 0f,
)

@Serializable
data class FlashcardRequest(
    @SerialName("source_text")
    val sourceText: String,

    @SerialName("target_text")
    val targetText: String,
)

@Serializable
data class FlashcardUpdateRequest(
    @SerialName("source_text")
    val sourceText: String? = null,

    @SerialName("target_text")
    val targetText: String? = null,

    @SerialName("srs_level")
    val srsLevel: Float? = null,
)

@Serializable
data class LoginRequest(
    @SerialName("id_token")
    val idToken: String,
)

@Serializable
data class LoginResponse(
    val token: String,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,

    @SerialName("display_name")
    val displayName: String?,

    @SerialName("streak_days") val streakDays: Int,
)

@Serializable
data class LearningCompleteRequest(
    @SerialName("cards_heard")
    val cardsHeard: Int,

    @SerialName("total_cards")
    val totalCards: Int,
)
