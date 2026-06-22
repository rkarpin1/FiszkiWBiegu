// Integration tests for the flashcard endpoints:
//   GET    /collections/{id}/flashcards
//   POST   /collections/{id}/flashcards
//   PUT    /flashcards/{id}
//   DELETE /flashcards/{id}
//
// Same pattern as the other suites: act over REST, assert HTTP status + JSON, then
// assert DB state directly. Collections and flashcards are created via REST; only
// users are seeded into the DB.

use crate::common::{client, spawn_app, TestApp};
use serde_json::{json, Value};
use uuid::Uuid;

// ---- helpers -------------------------------------------------------------------

async fn create_collection(app: &TestApp, token: &str) -> Uuid {
    let resp = client()
        .post(format!("{}/collections", app.base_url))
        .bearer_auth(token)
        .json(&json!({
            "name": "Zwierzęta",
            "description": "",
            "source_language": "pl",
            "target_language": "en"
        }))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 201, "collection create failed");
    let v = resp.json::<Value>().await.unwrap();
    Uuid::parse_str(v["id"].as_str().unwrap()).unwrap()
}

async fn create_flashcard(app: &TestApp, token: &str, collection_id: Uuid, src: &str, tgt: &str) -> Uuid {
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
    assert_eq!(resp.status().as_u16(), 201, "flashcard create failed");
    let v = resp.json::<Value>().await.unwrap();
    Uuid::parse_str(v["id"].as_str().unwrap()).unwrap()
}

// ---- GET /collections/{id}/flashcards ------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn list_flashcards_returns_sorted_by_position() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-list@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Arrange: three flashcards created in order.
    create_flashcard(&app, &token, coll, "kot", "cat").await;
    create_flashcard(&app, &token, coll, "pies", "dog").await;
    create_flashcard(&app, &token, coll, "ptak", "bird").await;

    // Act: list them.
    let resp = client()
        .get(format!("{}/collections/{}/flashcards", app.base_url, coll))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert: 200 and items are ordered by ascending position 0,1,2.
    assert_eq!(resp.status().as_u16(), 200);
    let items = resp.json::<Value>().await.unwrap();
    let arr = items.as_array().unwrap();
    assert_eq!(arr.len(), 3);
    assert_eq!(arr[0]["position"].as_i64(), Some(0));
    assert_eq!(arr[1]["position"].as_i64(), Some(1));
    assert_eq!(arr[2]["position"].as_i64(), Some(2));
    assert_eq!(arr[0]["source_text"], "kot");
    assert_eq!(arr[2]["source_text"], "ptak");
}

#[tokio::test(flavor = "multi_thread")]
async fn list_flashcards_of_another_users_collection_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("fc-list-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("fc-list-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;

    // Act: B lists A's collection's flashcards.
    let resp = client()
        .get(format!("{}/collections/{}/flashcards", app.base_url, coll))
        .bearer_auth(&token_b)
        .send()
        .await
        .unwrap();

    // Assert: 404 (ownership checked via verify_collection_owner).
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn list_flashcards_nonexistent_collection_is_404() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-list-missing@test.dev").await);

    // Act: list flashcards of a collection that does not exist.
    let resp = client()
        .get(format!(
            "{}/collections/{}/flashcards",
            app.base_url,
            Uuid::new_v4()
        ))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert: 404.
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn list_flashcards_without_jwt_is_401() {
    let app = spawn_app().await;
    // Act: no Authorization header.
    let resp = client()
        .get(format!(
            "{}/collections/{}/flashcards",
            app.base_url,
            Uuid::new_v4()
        ))
        .send()
        .await
        .unwrap();
    // Assert: 401 (rejected before ownership check).
    assert_eq!(resp.status().as_u16(), 401);
}

// ---- POST /collections/{id}/flashcards -----------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn post_flashcard_persists_and_autoincrements_position() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-post@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Act: create two flashcards.
    let id0 = create_flashcard(&app, &token, coll, "kot", "cat").await;
    let id1 = create_flashcard(&app, &token, coll, "pies", "dog").await;

    // Assert (DB): both rows exist with auto-incrementing positions 0 and 1.
    let (src0, pos0): (String, i32) =
        sqlx::query_as("SELECT source_text, position FROM flashcards WHERE id = $1")
            .bind(id0)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    let pos1: i32 = sqlx::query_scalar("SELECT position FROM flashcards WHERE id = $1")
        .bind(id1)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(src0, "kot");
    assert_eq!(pos0, 0);
    assert_eq!(pos1, 1);
}

#[tokio::test(flavor = "multi_thread")]
async fn post_flashcard_to_another_users_collection_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("fc-post-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("fc-post-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;

    // Act: B adds a flashcard to A's collection.
    let resp = client()
        .post(format!("{}/collections/{}/flashcards", app.base_url, coll))
        .bearer_auth(&token_b)
        .json(&json!({"source_text": "x", "target_text": "y"}))
        .send()
        .await
        .unwrap();

    // Assert: 404.
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn post_flashcard_missing_field_is_400() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-post-missing@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // Act: omit target_text -> serde/Json extractor rejects.
    let resp = client()
        .post(format!("{}/collections/{}/flashcards", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"source_text": "kot"}))
        .send()
        .await
        .unwrap();

    // Assert: 400.
    assert_eq!(resp.status().as_u16(), 400);
}

#[tokio::test(flavor = "multi_thread")]
async fn post_flashcard_empty_text_is_201_known_issue() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-post-empty@test.dev").await);
    let coll = create_collection(&app, &token).await;

    // KNOWN ISSUE (flashcards.rs:57-94): the create handler does NOT validate text
    // fields (unlike collection names), so empty strings are accepted and stored.
    // This asserts the current behavior to lock it in until validation is added.
    let resp = client()
        .post(format!("{}/collections/{}/flashcards", app.base_url, coll))
        .bearer_auth(&token)
        .json(&json!({"source_text": "", "target_text": ""}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 201);

    // DB confirms the empty strings were persisted.
    let id = Uuid::parse_str(resp.json::<Value>().await.unwrap()["id"].as_str().unwrap()).unwrap();
    let (src, tgt): (String, String) =
        sqlx::query_as("SELECT source_text, target_text FROM flashcards WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(src, "");
    assert_eq!(tgt, "");
}

// ---- PUT /flashcards/{id} ------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn put_flashcard_partial_update_source_only() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-put-src@test.dev").await);
    let coll = create_collection(&app, &token).await;
    let id = create_flashcard(&app, &token, coll, "kot", "cat").await;

    // Act: update only source_text; target_text must stay unchanged (COALESCE).
    let resp = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&json!({"source_text": "kotek"}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 200);

    // Assert (DB): source changed, target preserved.
    let (src, tgt): (String, String) =
        sqlx::query_as("SELECT source_text, target_text FROM flashcards WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(src, "kotek");
    assert_eq!(tgt, "cat");
}

#[tokio::test(flavor = "multi_thread")]
async fn put_flashcard_partial_update_srs_level_only() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-put-srs@test.dev").await);
    let coll = create_collection(&app, &token).await;
    let id = create_flashcard(&app, &token, coll, "kot", "cat").await;

    // Act: update only srs_level.
    let resp = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&json!({"srs_level": 0.5}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 200);

    // Assert (DB): srs_level updated, text fields untouched.
    let (srs, src): (f32, String) =
        sqlx::query_as("SELECT srs_level, source_text FROM flashcards WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert!((srs - 0.5).abs() < 1e-6, "srs_level = {srs}");
    assert_eq!(src, "kot");
}

#[tokio::test(flavor = "multi_thread")]
async fn put_flashcard_empty_body_is_noop() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-put-noop@test.dev").await);
    let coll = create_collection(&app, &token).await;
    let id = create_flashcard(&app, &token, coll, "kot", "cat").await;

    // Act: an empty JSON object updates nothing (all fields optional).
    let resp = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&json!({}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 200);

    // Assert (DB): values unchanged.
    let (src, tgt): (String, String) =
        sqlx::query_as("SELECT source_text, target_text FROM flashcards WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(src, "kot");
    assert_eq!(tgt, "cat");
}

#[tokio::test(flavor = "multi_thread")]
async fn put_flashcard_of_another_user_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("fc-put-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("fc-put-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;
    let id = create_flashcard(&app, &token_a, coll, "kot", "cat").await;

    // Act: B updates A's flashcard.
    let resp = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token_b)
        .json(&json!({"source_text": "hacked"}))
        .send()
        .await
        .unwrap();

    // Assert: 404 (ownership enforced via JOIN to collections.user_id).
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn put_flashcard_cannot_clear_last_studied_at_known_issue() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-put-null@test.dev").await);
    let coll = create_collection(&app, &token).await;
    let id = create_flashcard(&app, &token, coll, "kot", "cat").await;

    // Arrange: set last_studied_at to a concrete timestamp.
    let set = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&json!({"last_studied_at": "2026-01-01T00:00:00Z"}))
        .send()
        .await
        .unwrap();
    assert_eq!(set.status().as_u16(), 200);

    // Act: try to clear it back to null.
    let clear = client()
        .put(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&json!({"last_studied_at": null}))
        .send()
        .await
        .unwrap();
    assert_eq!(clear.status().as_u16(), 200);

    // KNOWN ISSUE (flashcards.rs:104-117): the update uses COALESCE($4, ...), so a
    // null last_studied_at means "no change" — it can never be reset to NULL via the
    // API. The timestamp therefore remains set. Assert that current behavior.
    let last: Option<chrono::DateTime<chrono::Utc>> =
        sqlx::query_scalar("SELECT last_studied_at FROM flashcards WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert!(last.is_some(), "last_studied_at was unexpectedly cleared to NULL");
}

// ---- DELETE /flashcards/{id} ---------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn delete_flashcard_removes_row() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("fc-del@test.dev").await);
    let coll = create_collection(&app, &token).await;
    let id = create_flashcard(&app, &token, coll, "kot", "cat").await;

    // Act: delete the flashcard.
    let resp = client()
        .delete(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert (HTTP): 204, and (DB): the row is gone.
    assert_eq!(resp.status().as_u16(), 204);
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM flashcards WHERE id = $1")
        .bind(id)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
}

#[tokio::test(flavor = "multi_thread")]
async fn delete_flashcard_of_another_user_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("fc-del-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("fc-del-b@test.dev").await);
    let coll = create_collection(&app, &token_a).await;
    let id = create_flashcard(&app, &token_a, coll, "kot", "cat").await;

    // Act: B deletes A's flashcard.
    let resp = client()
        .delete(format!("{}/flashcards/{}", app.base_url, id))
        .bearer_auth(&token_b)
        .send()
        .await
        .unwrap();

    // Assert: 404, and A's flashcard still exists.
    assert_eq!(resp.status().as_u16(), 404);
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM flashcards WHERE id = $1")
        .bind(id)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(count, 1);
}
