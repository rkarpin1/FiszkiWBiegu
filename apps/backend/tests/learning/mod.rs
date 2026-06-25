// Integration tests for the learning endpoints:
//   GET  /collections/{id}/learning
//   POST /collections/{id}/learning/complete
//
// Act over REST, assert HTTP status + JSON, then assert DB state directly.

use crate::common::{client, spawn_app, TestApp};
use serde_json::{json, Value};
use uuid::Uuid;

async fn create_collection(app: &TestApp, token: &str) -> Uuid {
    let resp = client()
        .post(format!("{}/collections", app.base_url))
        .bearer_auth(token)
        .json(&json!({
            "name": "Sesja",
            "description": "",
            "source_language": "pl",
            "target_language": "en"
        }))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 201);
    Uuid::parse_str(resp.json::<Value>().await.unwrap()["id"].as_str().unwrap()).unwrap()
}

async fn add_flashcard(app: &TestApp, token: &str, collection_id: Uuid, src: &str, tgt: &str) {
    let resp = client()
        .post(format!(
            "{}/collections/{}/flashcards",
            app.base_url, collection_id
        ))
        .bearer_auth(token)
        .json(&json!({"source_text": src, "target_text": tgt}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 201);
}

// ---- GET /collections/{id}/learning --------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn get_learning_returns_flashcards_sorted_by_position() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("learn-get@test.dev").await);
    let coll = create_collection(&app, &token).await;
    add_flashcard(&app, &token, coll, "kot", "cat").await;
    add_flashcard(&app, &token, coll, "pies", "dog").await;

    // Act: fetch the learning session.
    let resp = client()
        .get(format!("{}/collections/{}/learning", app.base_url, coll))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert: 200 and ordered by position.
    assert_eq!(resp.status().as_u16(), 200);
    let arr = resp.json::<Value>().await.unwrap();
    let items = arr.as_array().unwrap();
    assert_eq!(items.len(), 2);
    assert_eq!(items[0]["position"].as_i64(), Some(0));
    assert_eq!(items[1]["position"].as_i64(), Some(1));
}

#[tokio::test(flavor = "multi_thread")]
async fn get_learning_of_another_users_collection_is_200_empty_known_issue() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("learn-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("learn-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;
    add_flashcard(&app, &token_a, coll, "kot", "cat").await;

    // KNOWN ISSUE (learning.rs:16-22): this endpoint enforces ownership only via a
    // JOIN on collections.user_id, so requesting another user's (or a nonexistent)
    // collection returns 200 with an empty array instead of 404 — inconsistent with
    // GET /collections/{id}/flashcards, which returns 404. Assert the current behavior.
    let resp = client()
        .get(format!("{}/collections/{}/learning", app.base_url, coll))
        .bearer_auth(&token_b)
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 200);
    assert_eq!(resp.json::<Value>().await.unwrap().as_array().unwrap().len(), 0);
}

// ---- POST /collections/{id}/learning/complete ----------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn learning_complete_accumulates_minutes_and_stamps_last_studied() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("learn-complete@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Act 1: complete a session (10 minutes). progress is no longer persisted —
    // it is computed on read from the flashcards' decayLevel.
    let r1 = client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"session_minutes": 10}))
        .send()
        .await
        .unwrap();
    assert_eq!(r1.status().as_u16(), 204);

    // Assert (DB): minutes accumulated, last_studied stamped.
    let (minutes, last): (i32, Option<chrono::DateTime<chrono::Utc>>) =
        sqlx::query_as(
            "SELECT total_study_minutes, last_studied FROM collections WHERE id = $1",
        )
        .bind(coll)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(minutes, 10);
    assert!(last.is_some(), "last_studied should be set");

    // Act 2: a second session accumulates onto the total.
    let r2 = client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"session_minutes": 5}))
        .send()
        .await
        .unwrap();
    assert_eq!(r2.status().as_u16(), 204);

    let minutes2: i32 =
        sqlx::query_scalar("SELECT total_study_minutes FROM collections WHERE id = $1")
            .bind(coll)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(minutes2, 15); // 10 + 5
}

#[tokio::test(flavor = "multi_thread")]
async fn learning_complete_of_another_user_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("lc-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("lc-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;

    // Act: B completes a session on A's collection.
    let resp = client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token_b)
        .json(&json!({"session_minutes": 10}))
        .send()
        .await
        .unwrap();

    // Assert: 404.
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn learning_complete_missing_field_is_400() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("lc-missing@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Act: omit session_minutes -> serde/Json extractor rejects.
    let resp = client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({}))
        .send()
        .await
        .unwrap();

    // Assert: 400.
    assert_eq!(resp.status().as_u16(), 400);
}

#[tokio::test(flavor = "multi_thread")]
async fn learning_complete_negative_minutes_decrements_known_issue() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("lc-negative@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Arrange: accumulate 10 minutes.
    client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"session_minutes": 10}))
        .send()
        .await
        .unwrap();

    // KNOWN ISSUE (collections.rs:137-168): session_minutes is added with no bounds
    // check, so a negative value subtracts from total_study_minutes. Assert that.
    let resp = client()
        .post(format!("{}/collections/{}/learning/complete", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"session_minutes": -4}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 204);

    let minutes: i32 = sqlx::query_scalar("SELECT total_study_minutes FROM collections WHERE id = $1")
        .bind(coll)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(minutes, 6); // 10 + (-4) — negative input accepted
}
