// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

use actix_web::{get, App, HttpResponse, HttpServer, Responder};

#[get("/health")]
async fn health() -> impl Responder {
    HttpResponse::Ok().body("ok")
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8080);

    HttpServer::new(|| App::new().service(health))
        .bind(("0.0.0.0", port))?
        .run()
        .await
}
