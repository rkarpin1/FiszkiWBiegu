// Single integration-test binary. Every resource suite is a submodule so they all
// share one Postgres container (started once by `common::shared_pg`). Gated behind
// the `integration-tests` feature so plain `cargo test` (CI) skips it entirely.
//
// Run locally with Docker available:  cargo test --features integration-tests
#![cfg(feature = "integration-tests")]

mod auth;
mod common;

use common::{client, spawn_app};

#[tokio::test]
async fn info_returns_200_and_crate_name() {
    // Arrange: spawn the real server backed by the shared Postgres container.
    let app = spawn_app().await;
    // Act: hit the public /info endpoint (no auth required).
    let resp = client()
        .get(format!("{}/info", app.base_url))
        .send()
        .await
        .unwrap();
    // Assert: 200 OK and the body advertises the crate name.
    assert_eq!(resp.status().as_u16(), 200);
    let body = resp.text().await.unwrap();
    assert!(
        body.contains("fiszki-w-biegu-server"),
        "unexpected /info body: {body}"
    );
}
