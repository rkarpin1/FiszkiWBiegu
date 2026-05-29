use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
pub struct User {
    pub id: Uuid,
    pub google_id: String,
    pub email: String,
    pub display_name: Option<String>,
    pub streak_days: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub id_token: String,
}

#[derive(Debug, Serialize, FromRow)]
pub struct Collection {
    pub id: Uuid,
    pub user_id: Uuid,
    pub name: String,
    pub description: String,
    pub source_language: String,
    pub target_language: String,
    pub created_at: DateTime<Utc>,
    pub last_studied: Option<DateTime<Utc>>,
    pub progress: f64,
    pub flashcard_count: i64,
}

#[derive(Debug, Deserialize)]
pub struct CollectionRequest {
    pub name: String,
    pub description: String,
    pub source_language: String,
    pub target_language: String,
}

#[derive(Debug, Deserialize)]
pub struct LearningCompleteRequest {
    pub cards_heard: i32,
    pub total_cards: i32,
}

#[derive(Debug, Serialize, FromRow)]
pub struct Flashcard {
    pub id: Uuid,
    pub collection_id: Uuid,
    pub polish_text: String,
    pub english_text: String,
    pub position: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct FlashcardRequest {
    pub polish_text: String,
    pub english_text: String,
}

#[derive(Debug, Deserialize)]
pub struct FlashcardUpdateRequest {
    pub polish_text: Option<String>,
    pub english_text: Option<String>,
}
