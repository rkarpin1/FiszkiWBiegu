use actix_web::{
    error::{ErrorInternalServerError, ErrorUnauthorized},
    web, FromRequest, HttpRequest,
};
use jsonwebtoken::{decode, Algorithm, DecodingKey, Validation};
use serde::{Deserialize, Serialize};
use std::future::{ready, Ready};
use uuid::Uuid;

pub struct JwtSecret(pub String);

#[derive(Debug, Serialize, Deserialize)]
struct Claims {
    sub: String,
    exp: usize,
}

pub struct AuthUser {
    pub id: Uuid,
}

impl FromRequest for AuthUser {
    type Error = actix_web::Error;
    type Future = Ready<Result<Self, Self::Error>>;

    fn from_request(req: &HttpRequest, _: &mut actix_web::dev::Payload) -> Self::Future {
        let jwt_secret = match req.app_data::<web::Data<JwtSecret>>() {
            Some(s) => s.0.clone(),
            None => return ready(Err(ErrorInternalServerError("JWT secret not configured"))),
        };

        let auth_header = match req.headers().get("Authorization") {
            Some(h) => h,
            None => return ready(Err(ErrorUnauthorized("Missing Authorization header"))),
        };

        let auth_str = match auth_header.to_str() {
            Ok(s) => s,
            Err(_) => return ready(Err(ErrorUnauthorized("Invalid Authorization header"))),
        };

        let token = match auth_str.strip_prefix("Bearer ") {
            Some(t) => t,
            None => return ready(Err(ErrorUnauthorized("Invalid Authorization header format"))),
        };

        let decoding_key = DecodingKey::from_secret(jwt_secret.as_bytes());
        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_aud = false;

        let token_data = match decode::<Claims>(token, &decoding_key, &validation) {
            Ok(td) => td,
            Err(_) => return ready(Err(ErrorUnauthorized("Invalid or expired token"))),
        };

        let user_id = match Uuid::parse_str(&token_data.claims.sub) {
            Ok(id) => id,
            Err(_) => return ready(Err(ErrorUnauthorized("Invalid user ID in token"))),
        };

        ready(Ok(AuthUser { id: user_id }))
    }
}
