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
    HttpServer::new(|| App::new().service(health))
        .bind(("0.0.0.0", 8080))?
        .run()
        .await
}
