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
    pub total_study_minutes: i64,
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
    pub progress: f32,
    pub session_minutes: i32,
}

#[derive(Debug, Serialize, FromRow)]
pub struct Flashcard {
    pub id: Uuid,
    pub collection_id: Uuid,
    pub source_text: String,
    pub target_text: String,
    pub position: i32,
    pub created_at: DateTime<Utc>,
    pub srs_level: f32,
    pub last_studied_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize)]
pub struct FlashcardRequest {
    pub source_text: String,
    pub target_text: String,
}

#[derive(Debug, Deserialize)]
pub struct FlashcardUpdateRequest {
    pub source_text: Option<String>,
    pub target_text: Option<String>,
    pub srs_level: Option<f32>,
    pub last_studied_at: Option<DateTime<Utc>>,
}
