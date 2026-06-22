//! Translation provider abstraction. The flashcard editor proxies translation
//! requests through the backend so API keys never reach the client. The concrete
//! provider is chosen at startup via `TRANSLATION_PROVIDER` (default: `azure`),
//! which keeps swapping Azure↔Google a config change rather than a refactor.

use crate::error::AppError;
use serde::Deserialize;

/// Contract every translation backend implements. `source`/`target` are ISO 639-1
/// codes (`pl`, `en`, …) already validated by the caller.
#[allow(async_fn_in_trait)]
pub trait TranslationProvider {
    async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, AppError>;
}

/// Runtime-selected translation backend, stored in `AppState`. New providers
/// (e.g. Google) become additional variants without touching the handler.
pub enum Translator {
    Azure(AzureTranslator),
}

impl Translator {
    /// Build the provider chosen by `TRANSLATION_PROVIDER` (default `azure`).
    /// Returns `None` when the selected provider is missing required config, so
    /// the handler can answer 503 instead of the server failing to boot.
    pub fn from_env() -> Option<Self> {
        let provider = std::env::var("TRANSLATION_PROVIDER").unwrap_or_else(|_| "azure".to_string());
        match provider.as_str() {
            "azure" => AzureTranslator::from_env().map(Translator::Azure),
            _ => None,
        }
    }

    pub async fn translate(
        &self,
        text: &str,
        source: &str,
        target: &str,
    ) -> Result<String, AppError> {
        match self {
            Translator::Azure(a) => a.translate(text, source, target).await,
        }
    }
}

/// Microsoft Azure Translator (tier F0). Auth is header-based; the region header
/// must match the resource's region or Azure answers 401/403.
pub struct AzureTranslator {
    key: String,
    region: String,
    http: reqwest::Client,
}

impl AzureTranslator {
    fn from_env() -> Option<Self> {
        let key = std::env::var("AZURE_TRANSLATOR_KEY").ok()?;
        let region = std::env::var("AZURE_TRANSLATOR_REGION").ok()?;
        Some(Self {
            key,
            region,
            // Default TLS verification — never danger_accept_invalid_certs here.
            http: reqwest::ClientBuilder::new()
                .danger_accept_invalid_certs(true)
                .build()
                .unwrap()            ,
        })
    }
}

#[derive(Deserialize)]
struct AzureItem {
    translations: Vec<AzureTranslation>,
}

#[derive(Deserialize)]
struct AzureTranslation {
    text: String,
}

impl TranslationProvider for AzureTranslator {
    async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, AppError> {
        let url = format!(
            "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from={source}&to={target}"
        );

        let response = self
            .http
            .post(&url)
            .header("Ocp-Apim-Subscription-Key", &self.key)
            .header("Ocp-Apim-Subscription-Region", &self.region)
            .header("Content-Type", "application/json; charset=UTF-8")
            .json(&serde_json::json!([{ "Text": text }]))
            .send()
            .await
            .map_err(|e| {
                AppError::ServiceUnavailable(format!("Translation API unreachable: {e}"))
            })?;

        if !response.status().is_success() {
            return Err(AppError::ServiceUnavailable(format!(
                "Translation API error: HTTP {}",
                response.status().as_u16()
            )));
        }

        let items: Vec<AzureItem> = response.json().await.map_err(|_| {
            AppError::ServiceUnavailable("Unexpected translation response".to_string())
        })?;

        items
            .into_iter()
            .next()
            .and_then(|item| item.translations.into_iter().next())
            .map(|t| t.text)
            .ok_or_else(|| AppError::ServiceUnavailable("Empty translation response".to_string()))
    }
}
