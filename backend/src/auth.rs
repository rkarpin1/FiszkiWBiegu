use actix_web::{
    error::{ErrorInternalServerError, ErrorUnauthorized},
    web, FromRequest, HttpRequest,
};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use std::{
    future::{ready, Ready},
    time::{SystemTime, UNIX_EPOCH},
};
use uuid::Uuid;

pub struct JwtConfig {
    pub secret: String,
}

pub struct GoogleConfig {
    pub client_id: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,
    pub exp: usize,
}

pub struct AuthUser {
    pub id: Uuid,
}

pub fn create_jwt(user_id: Uuid, secret: &str) -> Result<String, jsonwebtoken::errors::Error> {
    let exp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs() as usize
        + 30 * 24 * 3600; // 30 dni

    encode(
        &Header::default(),
        &Claims { sub: user_id.to_string(), exp },
        &EncodingKey::from_secret(secret.as_bytes()),
    )
}

impl FromRequest for AuthUser {
    type Error = actix_web::Error;
    type Future = Ready<Result<Self, Self::Error>>;

    fn from_request(req: &HttpRequest, _: &mut actix_web::dev::Payload) -> Self::Future {
        let jwt_config = match req.app_data::<web::Data<JwtConfig>>() {
            Some(c) => c,
            None => return ready(Err(ErrorInternalServerError("JWT config missing"))),
        };

        let auth_header = match req.headers().get("Authorization") {
            Some(h) => h,
            None => return ready(Err(ErrorUnauthorized("Missing Authorization header"))),
        };

        let token = match auth_header.to_str().ok().and_then(|s| s.strip_prefix("Bearer ")) {
            Some(t) => t,
            None => return ready(Err(ErrorUnauthorized("Invalid Authorization header"))),
        };

        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_aud = false;

        let token_data = match decode::<Claims>(
            token,
            &DecodingKey::from_secret(jwt_config.secret.as_bytes()),
            &validation,
        ) {
            Ok(td) => td,
            Err(_) => return ready(Err(ErrorUnauthorized("Invalid or expired token"))),
        };

        match Uuid::parse_str(&token_data.claims.sub) {
            Ok(id) => ready(Ok(AuthUser { id })),
            Err(_) => ready(Err(ErrorUnauthorized("Invalid user ID in token"))),
        }
    }
}
