// Shared integration-test infrastructure.
//
// One Postgres 16 container is started once per test binary (via a process-global
// OnceCell) and a single connection pool is shared by every spawned server and by
// the tests' own DB assertions. Each test spawns the real Actix server in-process
// on an OS-assigned port. Test isolation relies on every test seeding its own user
// (unique google_id), because all application endpoints are scoped by user_id.

#![allow(dead_code)] // helpers are shared across resource suites; not every suite uses all of them

use std::net::TcpListener;
use std::sync::Mutex;

use fiszki_w_biegu_server::{create_jwt, run, AppState, GoogleConfig, JwtConfig};
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use testcontainers::runners::AsyncRunner;
use testcontainers::{ContainerAsync, ImageExt};
use testcontainers_modules::postgres::Postgres;
use tokio::sync::OnceCell;
use uuid::Uuid;

// Secret the test server validates JWTs with; tests mint tokens using the same value.
const JWT_SECRET: &str = "test-secret";

// Started once, kept alive for the whole test run. The container handle lives in the
// static so it is never dropped mid-run; testcontainers cleans it up at process exit.
static PG: OnceCell<PgShared> = OnceCell::const_new();

// Holds the shared container id so the process-exit hook can remove it.
static CONTAINER_ID: Mutex<Option<String>> = Mutex::new(None);

// testcontainers removes a container on Drop, but our container lives in a 'static
// OnceCell that is never dropped, and this crate ships no Ryuk reaper. So we record
// the id at startup and force-remove it when the test process exits.
#[ctor::dtor]
fn remove_shared_container() {
    if let Some(id) = CONTAINER_ID.lock().unwrap().take() {
        let _ = std::process::Command::new("docker")
            .args(["rm", "-f", &id])
            .output();
    }
}

struct PgShared {
    _container: ContainerAsync<Postgres>,
    pool: PgPool,
}

async fn shared_pg() -> &'static PgShared {
    PG.get_or_init(|| async {
        // Start a disposable Postgres container and build the shared pool.
        // Pin to 16-alpine: matches Supabase's major version and provides the
        // built-in gen_random_uuid() that migration 001 relies on (absent in <13).
        let container = Postgres::default()
            .with_tag("16-alpine")
            .start()
            .await
            .expect("failed to start postgres container");
        let port = container
            .get_host_port_ipv4(5432)
            .await
            .expect("failed to resolve mapped port");
        // Record the id so the process-exit hook can force-remove the container.
        *CONTAINER_ID.lock().unwrap() = Some(container.id().to_string());

        let conn = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");

        let pool = PgPoolOptions::new()
            .max_connections(20)
            .connect(&conn)
            .await
            .expect("failed to connect shared pool");

        // Apply all migrations once against the fresh database.
        fiszki_w_biegu_server::run_migrations(&pool)
            .await
            .expect("failed to run migrations");

        PgShared { _container: container, pool }
    })
    .await
}

pub struct TestApp {
    pub base_url: String,
    pub pool: PgPool,
    pub jwt_secret: String,
}

impl TestApp {
    /// Mint a JWT for `user_id` using the same secret the server validates with.
    pub fn jwt_for(&self, user_id: Uuid) -> String {
        create_jwt(user_id, &self.jwt_secret).expect("failed to mint jwt")
    }

    /// Seed a user directly into the DB (permitted by the test contract) and return its id.
    /// The google_id is randomized so concurrent tests never collide on the UNIQUE constraint.
    pub async fn seed_user(&self, email: &str) -> Uuid {
        sqlx::query_scalar::<_, Uuid>(
            "INSERT INTO users (google_id, email, display_name) VALUES ($1, $2, $3) RETURNING id",
        )
        .bind(Uuid::new_v4().to_string())
        .bind(email)
        .bind("Test User")
        .fetch_one(&self.pool)
        .await
        .expect("failed to seed user")
    }
}

/// Spawn the real server in-process, backed by the shared Postgres container.
pub async fn spawn_app() -> TestApp {
    spawn_app_with_deploy_key(None).await
}

/// Variant that lets deploy tests configure the deploy API key.
pub async fn spawn_app_with_deploy_key(deploy_api_key: Option<String>) -> TestApp {
    let shared = shared_pg().await;
    let pool = shared.pool.clone();

    // Bind to port 0 so the OS hands us a free port; read it back before serving.
    let listener = TcpListener::bind("127.0.0.1:0").expect("failed to bind test listener");
    let port = listener.local_addr().expect("failed to read local addr").port();

    let server = run(
        listener,
        pool.clone(),
        JwtConfig { secret: JWT_SECRET.to_string() },
        GoogleConfig { client_id: "test-client-id".to_string() },
        AppState { deploy_api_key },
    )
    .expect("failed to build server");

    // Drive the server in the background for the duration of the test.
    tokio::spawn(server);

    TestApp {
        base_url: format!("http://127.0.0.1:{port}"),
        pool,
        jwt_secret: JWT_SECRET.to_string(),
    }
}

/// HTTP client that does not auto-follow redirects, so tests assert raw status codes.
pub fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .expect("failed to build http client")
}
