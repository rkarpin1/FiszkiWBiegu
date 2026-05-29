use actix_web::{web, HttpResponse, Responder};
use serde_json::json;
use sqlx::PgPool;
use uuid::Uuid;

use crate::auth::AuthUser;
use crate::models::{Flashcard, FlashcardRequest, FlashcardUpdateRequest};

async fn verify_collection_owner(
    pool: &PgPool,
    collection_id: Uuid,
    user_id: Uuid,
) -> Result<bool, sqlx::Error> {
    let row =
        sqlx::query("SELECT id FROM collections WHERE id = $1 AND user_id = $2")
            .bind(collection_id)
            .bind(user_id)
            .fetch_optional(pool)
            .await?;
    Ok(row.is_some())
}

pub async fn list(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
) -> impl Responder {
    let collection_id = path.into_inner();

    match verify_collection_owner(pool.get_ref(), collection_id, user.id).await {
        Err(e) => {
            eprintln!("DB error verifying collection: {e}");
            return HttpResponse::InternalServerError().json(json!({"error": "Database error"}));
        }
        Ok(false) => {
            return HttpResponse::NotFound().json(json!({"error": "Collection not found"}));
        }
        Ok(true) => {}
    }

    let result = sqlx::query_as::<_, Flashcard>(
        "SELECT id, collection_id, polish_text, english_text, position, created_at FROM flashcards WHERE collection_id = $1 ORDER BY position",
    )
    .bind(collection_id)
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(flashcards) => HttpResponse::Ok().json(flashcards),
        Err(e) => {
            eprintln!("DB error listing flashcards: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn create(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
    body: web::Json<FlashcardRequest>,
) -> impl Responder {
    let collection_id = path.into_inner();

    match verify_collection_owner(pool.get_ref(), collection_id, user.id).await {
        Err(e) => {
            eprintln!("DB error verifying collection: {e}");
            return HttpResponse::InternalServerError().json(json!({"error": "Database error"}));
        }
        Ok(false) => {
            return HttpResponse::NotFound().json(json!({"error": "Collection not found"}));
        }
        Ok(true) => {}
    }

    let result = sqlx::query_as::<_, Flashcard>(
        r#"INSERT INTO flashcards (collection_id, polish_text, english_text, position)
           VALUES ($1, $2, $3, (SELECT COALESCE(MAX(position), -1) + 1 FROM flashcards WHERE collection_id = $1))
           RETURNING id, collection_id, polish_text, english_text, position, created_at"#,
    )
    .bind(collection_id)
    .bind(&body.polish_text)
    .bind(&body.english_text)
    .fetch_one(pool.get_ref())
    .await;

    match result {
        Ok(flashcard) => HttpResponse::Created().json(flashcard),
        Err(e) => {
            eprintln!("DB error creating flashcard: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn update(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
    body: web::Json<FlashcardUpdateRequest>,
) -> impl Responder {
    let id = path.into_inner();

    let result = sqlx::query_as::<_, Flashcard>(
        r#"UPDATE flashcards SET
               polish_text = COALESCE($1, flashcards.polish_text),
               english_text = COALESCE($2, flashcards.english_text)
           FROM collections
           WHERE flashcards.id = $3
             AND flashcards.collection_id = collections.id
             AND collections.user_id = $4
           RETURNING flashcards.id, flashcards.collection_id, flashcards.polish_text,
                     flashcards.english_text, flashcards.position, flashcards.created_at"#,
    )
    .bind(&body.polish_text)
    .bind(&body.english_text)
    .bind(id)
    .bind(user.id)
    .fetch_optional(pool.get_ref())
    .await;

    match result {
        Ok(Some(flashcard)) => HttpResponse::Ok().json(flashcard),
        Ok(None) => HttpResponse::NotFound().json(json!({"error": "Flashcard not found"})),
        Err(e) => {
            eprintln!("DB error updating flashcard: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn delete(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
) -> impl Responder {
    let id = path.into_inner();

    let result = sqlx::query(
        r#"DELETE FROM flashcards
           USING collections
           WHERE flashcards.id = $1
             AND flashcards.collection_id = collections.id
             AND collections.user_id = $2"#,
    )
    .bind(id)
    .bind(user.id)
    .execute(pool.get_ref())
    .await;

    match result {
        Ok(r) if r.rows_affected() == 0 => {
            HttpResponse::NotFound().json(json!({"error": "Flashcard not found"}))
        }
        Ok(_) => HttpResponse::NoContent().finish(),
        Err(e) => {
            eprintln!("DB error deleting flashcard: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}
