use actix_web::{web, HttpResponse, Responder};
use serde::Deserialize;
use serde_json::json;
use sqlx::PgPool;

use crate::auth::{create_jwt, AuthUser, GoogleConfig, JwtConfig};
use crate::models::{LoginRequest, User};

#[derive(Debug, Deserialize)]
struct GoogleTokenInfo {
    sub: String,
    email: String,
    aud: String,
    name: Option<String>,
}

async fn validate_google_token(
    id_token: &str,
    client_id: &str,
) -> Result<GoogleTokenInfo, String> {
    let url = format!("https://oauth2.googleapis.com/tokeninfo?id_token={id_token}");

    let http_client = reqwest::ClientBuilder::new()
        .danger_accept_invalid_certs(true)
        .build()
        .unwrap();

    let response = http_client.get(&url)
        .send()
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

    // `(xmax = 0)` jest prawdziwe wyłącznie dla wiersza świeżo wstawionego w tej
    // instrukcji; ścieżka `ON CONFLICT DO UPDATE` zwraca `xmax <> 0`. To pozwala
    // odróżnić rejestrację (nowy user) od ponownego logowania w jednym zapytaniu.
    #[derive(sqlx::FromRow)]
    struct UpsertedUser {
        #[sqlx(flatten)]
        user: User,
        is_new: bool,
    }

    let upserted = sqlx::query_as::<_, UpsertedUser>(
        r#"INSERT INTO users (google_id, email, display_name)
           VALUES ($1, $2, $3)
           ON CONFLICT (google_id)
             DO UPDATE SET email = EXCLUDED.email, display_name = EXCLUDED.display_name
           RETURNING id, google_id, email, display_name, streak_days, created_at, (xmax = 0) AS is_new"#,
    )
    .bind(&token_info.sub)
    .bind(&token_info.email)
    .bind(&token_info.name)
    .fetch_one(pool.get_ref())
    .await;

    let UpsertedUser { user, is_new } = match upserted {
        Ok(u) => u,
        Err(e) => {
            eprintln!("DB error upserting user: {e}");
            return HttpResponse::InternalServerError()
                .json(json!({"error": "Database error"}));
        }
    };

    // Best-effort: błąd seedowania (lub brak konta-szablonu) nie blokuje logowania.
    if is_new {
        if let Err(e) = crate::seed::seed_new_user(pool.get_ref(), user.id).await {
            eprintln!("seed error for new user {}: {e}", user.id);
        }
    }

    match create_jwt(user.id, &jwt_config.secret) {
        Ok(token) => HttpResponse::Ok().json(json!({"token": token})),
        Err(e) => {
            eprintln!("JWT error: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Token creation failed"}))
        }
    }
}

pub async fn me(pool: web::Data<PgPool>, user: AuthUser) -> impl Responder {
    let result = sqlx::query_as::<_, User>(
        "SELECT id, google_id, email, display_name, streak_days, created_at FROM users WHERE id = $1",
    )
    .bind(user.id)
    .fetch_optional(pool.get_ref())
    .await;

    match result {
        Ok(Some(u)) => HttpResponse::Ok().json(json!({
            "id": u.id,
            "email": u.email,
            "display_name": u.display_name,
            "streak_days": u.streak_days,
        })),
        Ok(None) => HttpResponse::NotFound().json(json!({"error": "User not found"})),
        Err(e) => {
            eprintln!("DB error fetching user: {e}");
            HttpResponse::InternalServerError().json(json!({"error": "Database error"}))
        }
    }
}
