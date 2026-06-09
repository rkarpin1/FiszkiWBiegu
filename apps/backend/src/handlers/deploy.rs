use crate::{AppState, error::AppError};
use actix_web::{HttpRequest, HttpResponse, web};
use log::{error, info, warn};
use std::fs::{metadata, set_permissions};
use std::io::Write;

pub async fn deploy(
    req: HttpRequest,
    body: web::Bytes,
    state: web::Data<AppState>,
) -> Result<HttpResponse, AppError> {
    let deploy_key = state.deploy_api_key.as_deref().ok_or_else(|| {
        AppError::ServiceUnavailable("Deploy endpoint is not configured".to_string())
    })?;

    let provided = req
        .headers()
        .get("X-Deploy-Key")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");

    if provided != deploy_key {
        warn!("Deploy rejected: invalid key");
        return Err(AppError::Unauthorized("Invalid deploy key".to_string()));
    }

    if body.is_empty() {
        return Err(AppError::BadRequest("Empty payload".to_string()));
    }

    let exe = std::env::current_exe()
        .map_err(|e| AppError::Internal(format!("Cannot resolve exe path: {e}")))?;
    let tmp = exe.with_extension("tmp");
    let bak = exe.with_extension("bak");

    {
        let mut f = std::fs::File::create(&tmp)
            .map_err(|e| AppError::Internal(format!("Cannot create tmp: {e}")))?;
        f.write_all(&body)
            .map_err(|e| AppError::Internal(format!("Cannot write tmp: {e}")))?;
        f.sync_all()
            .map_err(|e| AppError::Internal(format!("Cannot fsync tmp: {e}")))?;
    }

    let perms = metadata(&exe)
        .map_err(|e| AppError::Internal(format!("Cannot read permission exe: {e}")))?
        .permissions();

    std::fs::rename(&exe, &bak)
        .map_err(|e| AppError::Internal(format!("Cannot backup exe: {e}")))?;

    if let Err(e) = std::fs::rename(&tmp, &exe) {
        if let Err(re) = std::fs::rename(&bak, &exe) {
            error!("FATAL: failed to restore backup: {re}");
        }
        return Err(AppError::Internal(format!("Cannot rename tmp to exe: {e}")));
    }
    
    set_permissions(&exe, perms)
        .map_err(|e| AppError::Internal(format!("Cannot write permission exe: {e}")))?;

    tokio::spawn(async {
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
        std::process::exit(0);
    });

    info!("Deploy successful — process will restart");
    Ok(HttpResponse::Ok().json(serde_json::json!({ "status": "deploying" })))
}
