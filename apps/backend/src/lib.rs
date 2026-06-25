// Library crate: exposes app construction, migrations, and auth helpers so that
// both the binary (`main.rs`) and the integration tests under `tests/` can reuse them.

pub mod auth;
pub mod error;
pub mod handlers;
pub mod models;
pub mod seed;
pub mod translation;

use actix_cors::Cors;
use actix_web::dev::Server;
use actix_web::middleware::{Compress, Logger};
use actix_web::{get, web, App, HttpResponse, HttpServer, Responder};
use sqlx::PgPool;
use std::net::TcpListener;

// Re-export auth helpers used by the binary and by tests (e.g. minting JWTs in tests).
pub use auth::{create_jwt, Claims, GoogleConfig, JwtConfig};

use translation::Translator;

pub struct AppState {
    pub deploy_api_key: Option<String>,
    /// Selected translation backend, or `None` when not configured (handler → 503).
    pub translator: Option<Translator>,
}

#[get("/info")]
async fn info() -> impl Responder {
    HttpResponse::Ok().body(format!(
        "{} {}",
        env!("CARGO_PKG_NAME"),
        env!("CARGO_PKG_VERSION")
    ))
}

/// Apply all embedded migrations (`./migrations`, baked in at compile time) to the pool.
pub async fn run_migrations(pool: &PgPool) -> Result<(), sqlx::migrate::MigrateError> {
    sqlx::migrate!("./migrations").run(pool).await
}

/// Register every route. Shared by the binary and tests via `App::configure`.
pub fn register_routes(cfg: &mut web::ServiceConfig) {
    cfg.service(info)
        .service(
            web::scope("/auth")
                .route("/login", web::post().to(handlers::auth::login))
                .route("/me", web::get().to(handlers::auth::me)),
        )
        .service(
            web::scope("/collections")
                .route("", web::get().to(handlers::collections::list))
                .route("", web::post().to(handlers::collections::create))
                .route("/{id}", web::put().to(handlers::collections::update))
                .route("/{id}", web::delete().to(handlers::collections::delete))
                .route("/{id}/flashcards", web::get().to(handlers::flashcards::list))
                .route("/{id}/flashcards", web::post().to(handlers::flashcards::create))
                .route("/{id}/learning", web::get().to(handlers::learning::get_session))
                .route(
                    "/{id}/learning/complete",
                    web::post().to(handlers::collections::learning_complete),
                ),
        )
        .service(
            web::scope("/flashcards")
                .route("/{id}", web::put().to(handlers::flashcards::update))
                .route("/{id}", web::delete().to(handlers::flashcards::delete)),
        )
        .service(
            web::resource("/deploy")
                .app_data(web::PayloadConfig::new(100 * 1024 * 1024))
                .route(web::post().to(handlers::deploy::deploy)),
        )
        .route("/translate", web::post().to(handlers::translate::translate));
}

/// Build and start the HTTP server bound to an already-created listener.
/// Returns the `Server` future without awaiting it, so callers (binary or tests)
/// decide how to drive it. Binding to port 0 lets the OS pick a free port (tests).
pub fn run(
    listener: TcpListener,
    pool: PgPool,
    jwt_config: JwtConfig,
    google_config: GoogleConfig,
    app_state: AppState,
) -> std::io::Result<Server> {
    let pool = web::Data::new(pool);
    let jwt_config = web::Data::new(jwt_config);
    let google_config = web::Data::new(google_config);
    let app_state = web::Data::new(app_state);

    let server = HttpServer::new(move || {
        let cors = Cors::permissive();

        App::new()
            .wrap(cors)
            .wrap(Logger::default())
            .wrap(Compress::default())
            .app_data(pool.clone())
            .app_data(jwt_config.clone())
            .app_data(google_config.clone())
            .app_data(app_state.clone())
            .configure(register_routes)
    })
    .listen(listener)?
    .run();

    Ok(server)
}
