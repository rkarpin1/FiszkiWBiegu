// Integration tests for the /collections endpoints.
//
// Pattern for every test: act over REST (reqwest), assert the HTTP status + JSON,
// then assert the resulting state directly in the database via a separate pool.
// Collections are created through the API (never seeded directly); only users are
// seeded straight into the DB.

use crate::common::{client, spawn_app, TestApp};
use serde_json::{json, Value};
use uuid::Uuid;

// ---- helpers -------------------------------------------------------------------

fn valid_collection() -> Value {
    json!({
        "name": "Angielski",
        "description": "Podstawowe zwroty",
        "source_language": "pl",
        "target_language": "en"
    })
}

async fn post_collection(app: &TestApp, token: &str, body: &Value) -> (u16, Value) {
    let resp = client()
        .post(format!("{}/collections", app.base_url))
        .bearer_auth(token)
        .json(body)
        .send()
        .await
        .unwrap();
    let status = resp.status().as_u16();
    let v = resp.json::<Value>().await.unwrap_or(Value::Null);
    (status, v)
}

async fn create_collection_id(app: &TestApp, token: &str) -> Uuid {
    let (status, v) = post_collection(app, token, &valid_collection()).await;
    assert_eq!(status, 201, "expected created, got {status}: {v}");
    Uuid::parse_str(v["id"].as_str().expect("id in response")).expect("valid uuid")
}

async fn add_flashcard(app: &TestApp, token: &str, collection_id: Uuid) {
    // Flashcards are created via REST (other functionality must go through the API).
    let resp = client()
        .post(format!(
            "{}/collections/{}/flashcards",
            app.base_url, collection_id
        ))
        .bearer_auth(token)
        .json(&json!({"source_text": "kot", "target_text": "cat"}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 201, "flashcard create failed");
}

// ---- POST /collections ---------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_persists_to_db() {
    // Arrange: a seeded user with a valid JWT.
    let app = spawn_app().await;
    let user_id = app.seed_user("post-persist@test.dev").await;
    let token = app.jwt_for(user_id);

    // Act: create a collection over REST.
    let (status, body) = post_collection(&app, &token, &valid_collection()).await;

    // Assert (HTTP): 201 + response carries the submitted values and zeroed defaults.
    assert_eq!(status, 201);
    assert_eq!(body["name"], "Angielski");
    assert_eq!(body["source_language"], "pl");
    assert_eq!(body["target_language"], "en");
    assert_eq!(body["progress"].as_f64(), Some(0.0));
    assert_eq!(body["total_study_minutes"].as_i64(), Some(0));
    assert_eq!(body["flashcard_count"].as_i64(), Some(0));

    // Assert (DB): the row exists, owned by the seeded user, with the right values.
    let id = Uuid::parse_str(body["id"].as_str().unwrap()).unwrap();
    let (db_user, db_name, db_src, db_tgt, db_desc): (Uuid, String, String, String, String) =
        sqlx::query_as(
            "SELECT user_id, name, source_language, target_language, description \
             FROM collections WHERE id = $1",
        )
        .bind(id)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(db_user, user_id);
    assert_eq!(db_name, "Angielski");
    assert_eq!(db_src, "pl");
    assert_eq!(db_tgt, "en");
    assert_eq!(db_desc, "Podstawowe zwroty");
}

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_blank_name_is_422() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("blank-name@test.dev").await);

    // Act: whitespace-only name must be rejected by the handler's trim check.
    let mut body = valid_collection();
    body["name"] = json!("   ");
    let (status, v) = post_collection(&app, &token, &body).await;

    // Assert: 422 with the documented error message.
    assert_eq!(status, 422);
    assert_eq!(v["error"], "Name must not be blank");
}

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_identical_languages_is_422() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("same-lang@test.dev").await);

    // Act: source == target is forbidden.
    let mut body = valid_collection();
    body["target_language"] = json!("pl");
    let (status, v) = post_collection(&app, &token, &body).await;

    // Assert: 422.
    assert_eq!(status, 422);
    assert_eq!(v["error"], "Invalid or identical language codes");
}

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_unsupported_language_is_422() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("bad-lang@test.dev").await);

    // Act: a language code outside the accepted whitelist.
    let mut body = valid_collection();
    body["target_language"] = json!("xx");
    let (status, _v) = post_collection(&app, &token, &body).await;

    // Assert: 422.
    assert_eq!(status, 422);
}

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_missing_field_is_400() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("missing-field@test.dev").await);

    // Act: omit a required field -> serde/Json extractor rejects before the handler.
    let body = json!({"name": "X", "source_language": "pl", "target_language": "en"});
    let resp = client()
        .post(format!("{}/collections", app.base_url))
        .bearer_auth(&token)
        .json(&body)
        .send()
        .await
        .unwrap();

    // Assert: 400 (body is plain text from actix, so we don't parse JSON).
    assert_eq!(resp.status().as_u16(), 400);
}

#[tokio::test(flavor = "multi_thread")]
async fn post_collection_without_jwt_is_401() {
    let app = spawn_app().await;
    // Act: no Authorization header.
    let resp = client()
        .post(format!("{}/collections", app.base_url))
        .json(&valid_collection())
        .send()
        .await
        .unwrap();
    // Assert: 401.
    assert_eq!(resp.status().as_u16(), 401);
}

// ---- GET /collections ----------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn list_collections_is_user_scoped() {
    let app = spawn_app().await;
    let user_a = app.seed_user("owner-a@test.dev").await;
    let user_b = app.seed_user("owner-b@test.dev").await;
    let token_a = app.jwt_for(user_a);
    let token_b = app.jwt_for(user_b);

    // Arrange: A owns two collections, B owns one.
    create_collection_id(&app, &token_a).await;
    create_collection_id(&app, &token_a).await;
    create_collection_id(&app, &token_b).await;

    // Act: list as A.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .bearer_auth(&token_a)
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status().as_u16(), 200);
    let arr = resp.json::<Value>().await.unwrap();

    // Assert: only A's two collections are returned; none belongs to B.
    let items = arr.as_array().unwrap();
    assert_eq!(items.len(), 2);
    for item in items {
        assert_eq!(item["user_id"].as_str().unwrap(), user_a.to_string());
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn list_collections_empty_is_empty_array() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("empty-list@test.dev").await);

    // Act: a fresh user with no collections.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert: 200 and an empty array.
    assert_eq!(resp.status().as_u16(), 200);
    assert_eq!(resp.json::<Value>().await.unwrap().as_array().unwrap().len(), 0);
}

#[tokio::test(flavor = "multi_thread")]
async fn list_collections_flashcard_count_matches_db() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("count@test.dev").await);

    // Arrange: a collection with two flashcards (created via REST).
    let id = create_collection_id(&app, &token).await;
    add_flashcard(&app, &token, id).await;
    add_flashcard(&app, &token, id).await;

    // Act: list collections.
    let resp = client()
        .get(format!("{}/collections", app.base_url))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    let arr = resp.json::<Value>().await.unwrap();
    let item = &arr.as_array().unwrap()[0];

    // Assert (HTTP): reported flashcard_count is 2.
    assert_eq!(item["flashcard_count"].as_i64(), Some(2));

    // Assert (DB): matches the actual row count.
    let db_count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM flashcards WHERE collection_id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(db_count, 2);
}

// ---- PUT /collections/{id} -----------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn put_collection_updates_db() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("put-update@test.dev").await);
    let id = create_collection_id(&app, &token).await;

    // Act: update name + languages.
    let body = json!({
        "name": "Niemiecki",
        "description": "podróże",
        "source_language": "pl",
        "target_language": "de"
    });
    let resp = client()
        .put(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&body)
        .send()
        .await
        .unwrap();

    // Assert (HTTP): 200.
    assert_eq!(resp.status().as_u16(), 200);

    // Assert (DB): the row reflects the update.
    let (name, tgt): (String, String) =
        sqlx::query_as("SELECT name, target_language FROM collections WHERE id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(name, "Niemiecki");
    assert_eq!(tgt, "de");
}

#[tokio::test(flavor = "multi_thread")]
async fn put_collection_of_another_user_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("put-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("put-b@test.dev").await);

    // Arrange: A owns the collection.
    let id = create_collection_id(&app, &token_a).await;

    // Act: B tries to update it.
    let resp = client()
        .put(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token_b)
        .json(&valid_collection())
        .send()
        .await
        .unwrap();

    // Assert: 404 (cross-user resource is indistinguishable from missing).
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn put_collection_bad_uuid_is_404() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("put-baduuid@test.dev").await);

    // Act: a non-UUID path segment fails web::Path<Uuid> deserialization.
    let resp = client()
        .put(format!("{}/collections/not-a-uuid", app.base_url))
        .bearer_auth(&token)
        .json(&valid_collection())
        .send()
        .await
        .unwrap();

    // Assert: 404. Actix's Path extractor maps a deserialization error to 404
    // Not Found by default (unlike the Json extractor, which yields 400).
    assert_eq!(resp.status().as_u16(), 404);
}

#[tokio::test(flavor = "multi_thread")]
async fn put_collection_blank_name_is_422() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("put-blank@test.dev").await);
    let id = create_collection_id(&app, &token).await;

    // Act: blank name on update is rejected like on create.
    let mut body = valid_collection();
    body["name"] = json!("");
    let resp = client()
        .put(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&body)
        .send()
        .await
        .unwrap();

    // Assert: 422.
    assert_eq!(resp.status().as_u16(), 422);
}

#[tokio::test(flavor = "multi_thread")]
async fn put_collection_response_flashcard_count_is_always_zero_known_issue() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("put-count-bug@test.dev").await);
    let id = create_collection_id(&app, &token).await;
    add_flashcard(&app, &token, id).await;

    // Act: update the collection that has one flashcard.
    let resp = client()
        .put(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .json(&valid_collection())
        .send()
        .await
        .unwrap();
    let body = resp.json::<Value>().await.unwrap();

    // KNOWN ISSUE (collections.rs:88-93): the PUT response hardcodes flashcard_count
    // to 0 in its RETURNING clause, even though the collection actually has cards.
    // This asserts the current (buggy) behavior to lock it in until it is fixed.
    assert_eq!(body["flashcard_count"].as_i64(), Some(0));

    // The DB genuinely has one flashcard, proving the response value is wrong.
    let db_count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM flashcards WHERE collection_id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(db_count, 1);
}

// ---- DELETE /collections/{id} --------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn delete_collection_removes_row_and_cascades_flashcards() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("delete-cascade@test.dev").await);
    let id = create_collection_id(&app, &token).await;
    add_flashcard(&app, &token, id).await;
    add_flashcard(&app, &token, id).await;

    // Act: delete the collection.
    let resp = client()
        .delete(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert (HTTP): 204 No Content.
    assert_eq!(resp.status().as_u16(), 204);

    // Assert (DB): the collection row is gone and its flashcards cascaded away.
    let coll_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM collections WHERE id = $1")
        .bind(id)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(coll_count, 0);
    let card_count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM flashcards WHERE collection_id = $1")
            .bind(id)
            .fetch_one(&app.pool)
            .await
            .unwrap();
    assert_eq!(card_count, 0);
}

#[tokio::test(flavor = "multi_thread")]
async fn delete_collection_of_another_user_is_404() {
    let app = spawn_app().await;
    let token_a = app.jwt_for(app.seed_user("del-a@test.dev").await);
    let token_b = app.jwt_for(app.seed_user("del-b@test.dev").await);
    let id = create_collection_id(&app, &token_a).await;

    // Act: B tries to delete A's collection.
    let resp = client()
        .delete(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token_b)
        .send()
        .await
        .unwrap();

    // Assert: 404, and A's collection still exists.
    assert_eq!(resp.status().as_u16(), 404);
    let still_there: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM collections WHERE id = $1")
        .bind(id)
        .fetch_one(&app.pool)
        .await
        .unwrap();
    assert_eq!(still_there, 1);
}

#[tokio::test(flavor = "multi_thread")]
async fn delete_collection_twice_is_404() {
    let app = spawn_app().await;
    let token = app.jwt_for(app.seed_user("del-twice@test.dev").await);
    let id = create_collection_id(&app, &token).await;

    // Act: first delete succeeds.
    let first = client()
        .delete(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();
    assert_eq!(first.status().as_u16(), 204);

    // Act: second delete of the same id now finds nothing.
    let second = client()
        .delete(format!("{}/collections/{}", app.base_url, id))
        .bearer_auth(&token)
        .send()
        .await
        .unwrap();

    // Assert: 404.
    assert_eq!(second.status().as_u16(), 404);
}
