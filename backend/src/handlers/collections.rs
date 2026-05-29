use actix_web::{web, HttpResponse, Responder};
use serde_json::json;
use sqlx::PgPool;
use uuid::Uuid;

use crate::auth::AuthUser;
use crate::models::{Collection, CollectionRequest, LearningCompleteRequest};

const VALID_LANGUAGES: &[&str] = &["pl", "en", "de", "es", "fr", "it"];

fn validate_languages(src: &str, tgt: &str) -> bool {
    VALID_LANGUAGES.contains(&src) && VALID_LANGUAGES.contains(&tgt) && src != tgt
}

pub async fn list(pool: web::Data<PgPool>, user: AuthUser) -> impl Responder {
    let result = sqlx::query_as::<_, Collection>(
        "SELECT id, user_id, name, description, source_language, target_language, created_at, last_studied, progress \
         FROM collections WHERE user_id = $1 ORDER BY created_at DESC",
    )
    .bind(user.id)
    .fetch_all(pool.get_ref())
    .await;

    match result {
        Ok(collections) => HttpResponse::Ok().json(collections),
        Err(e) => {
            eprintln!("DB error listing collections: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn create(
    pool: web::Data<PgPool>,
    user: AuthUser,
    body: web::Json<CollectionRequest>,
) -> impl Responder {
    if body.name.trim().is_empty() {
        return HttpResponse::UnprocessableEntity()
            .json(json!({"error": "Name must not be blank"}));
    }
    if !validate_languages(&body.source_language, &body.target_language) {
        return HttpResponse::UnprocessableEntity()
            .json(json!({"error": "Invalid or identical language codes"}));
    }

    let result = sqlx::query_as::<_, Collection>(
        "INSERT INTO collections (user_id, name, description, source_language, target_language) \
         VALUES ($1, $2, $3, $4, $5) \
         RETURNING id, user_id, name, description, source_language, target_language, created_at, last_studied, progress",
    )
    .bind(user.id)
    .bind(&body.name)
    .bind(&body.description)
    .bind(&body.source_language)
    .bind(&body.target_language)
    .fetch_one(pool.get_ref())
    .await;

    match result {
        Ok(collection) => HttpResponse::Created().json(collection),
        Err(e) => {
            eprintln!("DB error creating collection: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn update(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
    body: web::Json<CollectionRequest>,
) -> impl Responder {
    if body.name.trim().is_empty() {
        return HttpResponse::UnprocessableEntity()
            .json(json!({"error": "Name must not be blank"}));
    }
    if !validate_languages(&body.source_language, &body.target_language) {
        return HttpResponse::UnprocessableEntity()
            .json(json!({"error": "Invalid or identical language codes"}));
    }

    let id = path.into_inner();
    let result = sqlx::query_as::<_, Collection>(
        "UPDATE collections SET name = $1, description = $2, source_language = $3, target_language = $4 \
         WHERE id = $5 AND user_id = $6 \
         RETURNING id, user_id, name, description, source_language, target_language, created_at, last_studied, progress",
    )
    .bind(&body.name)
    .bind(&body.description)
    .bind(&body.source_language)
    .bind(&body.target_language)
    .bind(id)
    .bind(user.id)
    .fetch_optional(pool.get_ref())
    .await;

    match result {
        Ok(Some(collection)) => HttpResponse::Ok().json(collection),
        Ok(None) => HttpResponse::NotFound().json(json!({"error": "Collection not found"})),
        Err(e) => {
            eprintln!("DB error updating collection: {e}");
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
    let result = sqlx::query("DELETE FROM collections WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(user.id)
        .execute(pool.get_ref())
        .await;

    match result {
        Ok(r) if r.rows_affected() == 0 => {
            HttpResponse::NotFound().json(json!({"error": "Collection not found"}))
        }
        Ok(_) => HttpResponse::NoContent().finish(),
        Err(e) => {
            eprintln!("DB error deleting collection: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}

pub async fn learning_complete(
    pool: web::Data<PgPool>,
    user: AuthUser,
    path: web::Path<Uuid>,
    body: web::Json<LearningCompleteRequest>,
) -> impl Responder {
    let id = path.into_inner();
    let progress = if body.total_cards > 0 {
        (body.cards_heard as f64 / body.total_cards as f64).clamp(0.0, 1.0)
    } else {
        0.0f64
    };

    let result = sqlx::query(
        "UPDATE collections SET last_studied = NOW(), progress = $1 WHERE id = $2 AND user_id = $3",
    )
    .bind(progress)
    .bind(id)
    .bind(user.id)
    .execute(pool.get_ref())
    .await;

    match result {
        Ok(r) if r.rows_affected() == 0 => {
            HttpResponse::NotFound().json(json!({"error": "Collection not found"}))
        }
        Ok(_) => HttpResponse::NoContent().finish(),
        Err(e) => {
            eprintln!("DB error updating learning progress: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}
