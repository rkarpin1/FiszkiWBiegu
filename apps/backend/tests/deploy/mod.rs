// Integration tests for POST /deploy — REJECTION PATHS ONLY.
//
// The happy path replaces the running binary and kills the process (deploy.rs:60-68),
// so it must never be exercised. We only assert the guard clauses: not-configured
// (503), bad key (401), and empty payload (400). Every request here either fails
// auth before the body is read, or sends an empty body — never real binary bytes.

use crate::common::{client, spawn_app, spawn_app_with_deploy_key};

#[tokio::test(flavor = "multi_thread")]
async fn deploy_not_configured_is_503() {
    // Arrange: server started with no deploy key configured.
    let app = spawn_app().await; // deploy_api_key = None
    // Act: any /deploy call.
    let resp = client()
        .post(format!("{}/deploy", app.base_url))
        .header("X-Deploy-Key", "whatever")
        .send()
        .await
        .unwrap();
    // Assert: 503 with the documented message.
    assert_eq!(resp.status().as_u16(), 503);
    assert_eq!(
        resp.json::<serde_json::Value>().await.unwrap()["error"],
        "Deploy endpoint is not configured"
    );
}

#[tokio::test(flavor = "multi_thread")]
async fn deploy_missing_key_is_401() {
    // Arrange: deploy key configured.
    let app = spawn_app_with_deploy_key(Some("s3cret".to_string())).await;
    // Act: no X-Deploy-Key header.
    let resp = client()
        .post(format!("{}/deploy", app.base_url))
        .send()
        .await
        .unwrap();
    // Assert: 401 (rejected before the body is read).
    assert_eq!(resp.status().as_u16(), 401);
}

#[tokio::test(flavor = "multi_thread")]
async fn deploy_wrong_key_is_401() {
    let app = spawn_app_with_deploy_key(Some("s3cret".to_string())).await;
    // Act: wrong key.
    let resp = client()
        .post(format!("{}/deploy", app.base_url))
        .header("X-Deploy-Key", "nope")
        .send()
        .await
        .unwrap();
    // Assert: 401.
    assert_eq!(resp.status().as_u16(), 401);
    assert_eq!(
        resp.json::<serde_json::Value>().await.unwrap()["error"],
        "Invalid deploy key"
    );
}

#[tokio::test(flavor = "multi_thread")]
async fn deploy_correct_key_empty_body_is_400() {
    let app = spawn_app_with_deploy_key(Some("s3cret".to_string())).await;
    // Act: correct key but an EMPTY body — rejected before any filesystem work.
    let resp = client()
        .post(format!("{}/deploy", app.base_url))
        .header("X-Deploy-Key", "s3cret")
        .send()
        .await
        .unwrap();
    // Assert: 400 Empty payload (the binary is never touched).
    assert_eq!(resp.status().as_u16(), 400);
    assert_eq!(
        resp.json::<serde_json::Value>().await.unwrap()["error"],
        "Empty payload"
    );
}
