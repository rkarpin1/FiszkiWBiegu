// Cross-cutting authentication tests.
//
// Every protected endpoint goes through the `AuthUser` extractor, which rejects
// missing or malformed credentials with 401 before any handler runs. We exercise
// that on a representative protected route: GET /collections.

use crate::common::{client, spawn_app};
use jsonwebtoken::{encode, EncodingKey, Header};
use serde::Serialize;
use uuid::Uuid;

// Mirrors the server's Claims shape so we can forge tokens for the negative cases.
#[derive(Serialize)]
struct TestClaims {
    sub: String,
    exp: usize,
}

fn far_future_exp() -> usize {
    (std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs()
        + 3600) as usize
}

#[tokio::test(flavor = "multi_thread")]
async fn missing_authorization_header_is_401() {
    // Arrange: a running server.
    let app = spawn_app().await;
    // Act: call a protected endpoint with no Authorization header.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .send()
        .await
        .unwrap();
    // Assert: rejected with 401 before reaching the handler.
    assert_eq!(resp.status().as_u16(), 401);
}

#[tokio::test(flavor = "multi_thread")]
async fn garbage_bearer_token_is_401() {
    let app = spawn_app().await;
    // Act: send a Bearer value that is not a JWT at all.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .header("Authorization", "Bearer not-a-jwt")
        .send()
        .await
        .unwrap();
    // Assert: 401.
    assert_eq!(resp.status().as_u16(), 401);
}

#[tokio::test(flavor = "multi_thread")]
async fn token_signed_with_wrong_secret_is_401() {
    let app = spawn_app().await;
    // Arrange: a structurally valid JWT, but signed with a secret the server does not use.
    let claims = TestClaims {
        sub: Uuid::new_v4().to_string(),
        exp: far_future_exp(),
    };
    let token = encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(b"definitely-the-wrong-secret"),
    )
    .unwrap();
    // Act + Assert: signature verification fails -> 401.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .header("Authorization", format!("Bearer {token}"))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 401);
}

#[tokio::test(flavor = "multi_thread")]
async fn token_with_non_uuid_subject_is_401() {
    let app = spawn_app().await;
    // Arrange: correctly signed (right secret) but `sub` is not a UUID.
    let claims = TestClaims {
        sub: "not-a-uuid".to_string(),
        exp: far_future_exp(),
    };
    let token = encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(app.jwt_secret.as_bytes()),
    )
    .unwrap();
    // Act + Assert: signature OK, but UUID parse of the subject fails -> 401.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .header("Authorization", format!("Bearer {token}"))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 401);
}
