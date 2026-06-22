// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

use std::net::TcpListener;

use flexi_logger::{Age, Cleanup, Criterion, DeferredNow, FileSpec, Naming, WriteMode, TS_DASHES_BLANK_COLONS_DOT_BLANK};
use log::{error, Record};

use fiszki_w_biegu_server::{run, run_migrations, AppState, GoogleConfig, JwtConfig};

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

    std::panic::set_hook(Box::new(|panic_info| {
        let msg = panic_info
            .payload()
            .downcast_ref::<&str>()
            .copied()
            .or_else(|| panic_info.payload().downcast_ref::<String>().map(String::as_str))
            .unwrap_or("unknown panic");
        let location = panic_info
            .location()
            .map(|l| format!("{}:{}", l.file(), l.line()))
            .unwrap_or_else(|| "unknown location".to_string());
        error!("PANIC at {location}: {msg}");
    }));

    let pool = sqlx::PgPool::connect(&database_url)
        .await
        .expect("Failed to connect to database");

    run_migrations(&pool)
        .await
        .expect("Failed to run database migrations");

    let listener = TcpListener::bind(("0.0.0.0", port))?;

    run(
        listener,
        pool,
        JwtConfig { secret: jwt_secret },
        GoogleConfig { client_id: google_client_id },
        AppState { deploy_api_key },
    )?
    .await
}
