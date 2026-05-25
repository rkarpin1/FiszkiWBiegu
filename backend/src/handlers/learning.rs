use actix_web::{web, HttpResponse, Responder};
use serde_json::json;
use sqlx::PgPool;
use uuid::Uuid;

use crate::auth::AuthUser;
use crate::models::Flashcard;

pub async fn get_session(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
) -> impl Responder {
    let collection_id = path.into_inner();

    let result = sqlx::query_as::<_, Flashcard>(
        r#"SELECT f.id, f.collection_id, f.polish_text, f.english_text, f.position, f.created_at
           FROM flashcards f
           JOIN collections c ON f.collection_id = c.id
           WHERE f.collection_id = $1 AND c.user_id = $2
           ORDER BY f.position"#,
    )
    .bind(collection_id)
    .bind(user.id)
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(flashcards) => HttpResponse::Ok().json(flashcards),
        Err(e) => {
            eprintln!("DB error loading learning session: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}
