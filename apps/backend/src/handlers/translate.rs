use actix_web::{web, HttpResponse};

use crate::auth::AuthUser;
use crate::error::AppError;
use crate::handlers::collections::validate_languages;
use crate::models::{TranslateRequest, TranslateResponse};
use crate::AppState;

/// Proxy a translation through the configured provider. JWT-protected like the
/// rest of the API. Language codes are validated exactly like collections
/// (must be in the accepted set and source ≠ target).
pub async fn translate(
    state: web::Data<AppState>,
    _user: AuthUser,
    body: web::Json<TranslateRequest>,
) -> Result<HttpResponse, AppError> {
    let text = body.source_text.trim();
    if text.is_empty() {
        return Err(AppError::BadRequest(
            "source_text must not be blank".to_string(),
        ));
    }
    if !validate_languages(&body.source_language, &body.target_language) {
        return Err(AppError::UnprocessableEntity(
            "Invalid or identical language codes".to_string(),
        ));
    }

    let translator = state
        .translator
        .as_ref()
        .ok_or_else(|| AppError::ServiceUnavailable("Translation is not configured".to_string()))?;

    let translated_text = translator
        .translate(text, &body.source_language, &body.target_language)
        .await?;

    Ok(HttpResponse::Ok().json(TranslateResponse { translated_text }))
}
