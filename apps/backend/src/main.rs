// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

mod auth;
pub mod error;
mod handlers;
mod models;

pub struct AppState {
    pub deploy_api_key: Option<String>,
}

use actix_cors::Cors;
use actix_web::{get, web, App, HttpResponse, HttpServer, Responder};
use actix_web::middleware::{Compress, Logger};
use flexi_logger::{Age, Cleanup, Criterion, DeferredNow, FileSpec, Naming, WriteMode, TS_DASHES_BLANK_COLONS_DOT_BLANK};
use log::{error, Record};
use auth::{GoogleConfig, JwtConfig};

pub fn main_format(
    w: &mut dyn std::io::Write,
    now: &mut DeferredNow,
    record: &Record,
) -> Result<(), std::io::Error> {
    write!(
        w,
        "[{}] {} [{}] {}",
        now.format(TS_DASHES_BLANK_COLONS_DOT_BLANK),
        record.level(),
        record.module_path().unwrap_or("<unnamed>"),
        record.args()
    )
}

#[get("/health")]
async fn health() -> impl Responder {
    HttpResponse::Ok().body("ok")
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv::dotenv().ok();

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8080);

    let database_url = std::env::var("DATABASE_URL").expect("DATABASE_URL must be set");
    let jwt_secret = std::env::var("JWT_SECRET").expect("JWT_SECRET must be set");
    let google_client_id =
        std::env::var("GOOGLE_CLIENT_ID").expect("GOOGLE_CLIENT_ID must be set");

    let deploy_api_key = std::env::var("DEPLOY_API_KEY").ok();

    let _handle = flexi_logger::Logger::try_with_str("info")
        .unwrap()
        .log_to_file(FileSpec::default().directory("logs").basename("log"))
        .write_mode(WriteMode::Async)
        .format(main_format)
        .rotate(
            Criterion::Age(Age::Day),
            Naming::Timestamps,
            Cleanup::KeepLogFiles(14),
        )
        .start()
        .unwrap();

    std::panic::set_hook(Box::new(|info| {
        let msg = info
            .payload()
            .downcast_ref::<&str>()
            .copied()
            .or_else(|| info.payload().downcast_ref::<String>().map(String::as_str))
            .unwrap_or("unknown panic");
        let location = info
            .location()
            .map(|l| format!("{}:{}", l.file(), l.line()))
            .unwrap_or_else(|| "unknown location".to_string());
        error!("PANIC at {location}: {msg}");
    }));



    let pool = sqlx::PgPool::connect(&database_url)
        .await
        .expect("Failed to connect to database");

    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("Failed to run database migrations");

    let pool = web::Data::new(pool);
    let jwt_config = web::Data::new(JwtConfig { secret: jwt_secret });
    let google_config = web::Data::new(GoogleConfig { client_id: google_client_id });
    let app_state = web::Data::new(AppState { deploy_api_key });

    HttpServer::new(move || {
        let cors = Cors::permissive();

        App::new()
            .wrap(cors)
            .wrap(Logger::default())
            .wrap(Compress::default())
            .app_data(pool.clone())
            .app_data(jwt_config.clone())
            .app_data(google_config.clone())
            .app_data(app_state.clone())
            .service(health)
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
                    .route("/{id}/learning/complete", web::post().to(handlers::collections::learning_complete)),
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
    })
    .bind(("0.0.0.0", port))?
    .run()
    .await
}
