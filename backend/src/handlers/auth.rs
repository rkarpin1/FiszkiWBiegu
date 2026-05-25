use actix_web::{web, HttpResponse, Responder};
use serde::Deserialize;
use serde_json::json;
use sqlx::PgPool;

use crate::auth::{create_jwt, GoogleConfig, JwtConfig};
use crate::models::{LoginRequest, User};

#[derive(Debug, Deserialize)]
struct GoogleTokenInfo {
    sub: String,
    email: String,
    aud: String,
}

async fn validate_google_token(
    id_token: &str,
    client_id: &str,
) -> Result<GoogleTokenInfo, String> {
    let url = format!("https://oauth2.googleapis.com/tokeninfo?id_token={id_token}");
    let response = reqwest::get(&url)
        .await
        .map_err(|e| format!("Google API unreachable: {e}"))?;

    if !response.status().is_success() {
        return Err("Invalid Google token".to_string());
    }

    let info: GoogleTokenInfo = response
        .json()
        .await
        .map_err(|_| "Unexpected Google response".to_string())?;

    if info.aud != client_id {
        return Err("Token audience mismatch".to_string());
    }

    Ok(info)
}

pub async fn login(
    pool: web::Data<PgPool>,
    google_config: web::Data<GoogleConfig>,
    jwt_config: web::Data<JwtConfig>,
    body: web::Json<LoginRequest>,
) -> impl Responder {
    let token_info =
        match validate_google_token(&body.id_token, &google_config.client_id).await {
            Ok(info) => info,
            Err(e) => return HttpResponse::Unauthorized().json(json!({"error": e})),
        };

    let user = sqlx::query_as::<_, User>(
        r#"INSERT INTO users (google_id, email) VALUES ($1, $2)
           ON CONFLICT (google_id) DO UPDATE SET email = EXCLUDED.email
           RETURNING id, google_id, email, created_at"#,
    )
    .bind(&token_info.sub)
    .bind(&token_info.email)
    .fetch_one(pool.get_ref())
    .await;

    let user = match user {
        Ok(u) => u,
        Err(e) => {
            eprintln!("DB error upserting user: {e}");
            return HttpResponse::InternalServerError()
                .json(json!({"error": "Database error"}));
        }
    };

    match create_jwt(user.id, &jwt_config.secret) {
        Ok(token) => HttpResponse::Ok().json(json!({"token": token})),
        Err(e) => {
            eprintln!("JWT error: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Token creation failed"}))
        }
    }
}
