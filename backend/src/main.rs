// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

mod auth;
mod handlers;
mod models;

use actix_cors::Cors;
use actix_web::{get, web, App, HttpResponse, HttpServer, Responder};
use auth::{GoogleConfig, JwtConfig};

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

    HttpServer::new(move || {
        let cors = Cors::permissive();

        App::new()
            .wrap(cors)
            .app_data(pool.clone())
            .app_data(jwt_config.clone())
            .app_data(google_config.clone())
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
    })
    .bind(("0.0.0.0", port))?
    .run()
    .await
}
