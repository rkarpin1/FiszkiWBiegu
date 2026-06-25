use sqlx::PgPool;
use uuid::Uuid;

/// Konto-szablon, z którego kopiujemy kolekcje i fiszki dla każdego nowego
/// użytkownika przy pierwszej rejestracji.
const SEED_TEMPLATE_EMAIL: &str = "rkarpin1@gmail.com";

/// Kopiuje wszystkie kolekcje i fiszki konta-szablonu ([`SEED_TEMPLATE_EMAIL`])
/// do nowego użytkownika. Cała kopia jest objęta jedną transakcją
/// (wszystko-albo-nic). Postęp nauki jest resetowany: wstawiamy te same podzbiory
/// kolumn co handlery `create` (collections / flashcards), więc `srs_level`,
/// `last_studied_at`, `last_studied` i `total_study_minutes` pochodzą z domyślnych
/// wartości tabel.
///
/// No-op (zwraca `Ok`), gdy konto-szablon nie istnieje lub jest tożsame z nowym
/// użytkownikiem. Wywołanie jest best-effort po stronie logowania — ewentualny
/// błąd nie blokuje wydania tokenu.
pub async fn seed_new_user(pool: &PgPool, new_user_id: Uuid) -> Result<(), sqlx::Error> {
    let template_id: Option<Uuid> = sqlx::query_scalar(
        "SELECT id FROM users WHERE email = $1",
    )
    .bind(SEED_TEMPLATE_EMAIL)
    .fetch_optional(pool)
    .await?;

    let template_id = match template_id {
        Some(id) if id != new_user_id => id,
        // Brak szablonu lub szablon == nowy użytkownik → nic do skopiowania.
        _ => return Ok(()),
    };

    let mut tx = pool.begin().await?;

    let collections: Vec<(Uuid, String, String, String, String)> = sqlx::query_as(
        "SELECT id, name, description, source_language, target_language \
         FROM collections WHERE user_id = $1",
    )
    .bind(template_id)
    .fetch_all(&mut *tx)
    .await?;

    for (old_id, name, description, source_language, target_language) in collections {
        let new_id: Uuid = sqlx::query_scalar(
            "INSERT INTO collections (user_id, name, description, source_language, target_language) \
             VALUES ($1, $2, $3, $4, $5) RETURNING id",
        )
        .bind(new_user_id)
        .bind(&name)
        .bind(&description)
        .bind(&source_language)
        .bind(&target_language)
        .fetch_one(&mut *tx)
        .await?;

        sqlx::query(
            "INSERT INTO flashcards (collection_id, source_text, target_text, position) \
             SELECT $1, source_text, target_text, position \
             FROM flashcards WHERE collection_id = $2",
        )
        .bind(new_id)
        .bind(old_id)
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(())
}
