use actix_web::HttpResponse;
use actix_web::ResponseError;
use std::fmt;

#[derive(Debug)]
pub enum AppError {
    BadRequest(String),
    Unauthorized(String),
    UnprocessableEntity(String),
    Internal(String),
    ServiceUnavailable(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::BadRequest(m) => write!(f, "{m}"),
            AppError::Unauthorized(m) => write!(f, "{m}"),
            AppError::UnprocessableEntity(m) => write!(f, "{m}"),
            AppError::Internal(m) => write!(f, "{m}"),
            AppError::ServiceUnavailable(m) => write!(f, "{m}"),
        }
    }
}

impl ResponseError for AppError {
    fn error_response(&self) -> HttpResponse {
        let msg = self.to_string();
        match self {
            AppError::BadRequest(_) => HttpResponse::BadRequest().json(serde_json::json!({ "error": msg })),
            AppError::Unauthorized(_) => HttpResponse::Unauthorized().json(serde_json::json!({ "error": msg })),
            AppError::UnprocessableEntity(_) => HttpResponse::UnprocessableEntity().json(serde_json::json!({ "error": msg })),
            AppError::Internal(_) => HttpResponse::InternalServerError().json(serde_json::json!({ "error": msg })),
            AppError::ServiceUnavailable(_) => HttpResponse::ServiceUnavailable().json(serde_json::json!({ "error": msg })),
        }
    }
}
