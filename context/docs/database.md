# Database — FiszkiWBiegu

The backend uses **Supabase (PostgreSQL)**. It connects via `sqlx` using
`DATABASE_URL` (session-mode pooler, port 5432). Server-side always uses the
`SUPABASE_SERVICE_ROLE_KEY` — never the anon key.

Migrations live in `apps/backend/migrations/`, are embedded into the binary
(`sqlx::migrate!`), and run on server startup.
**Next migration number: 012.**

## Schema (state after all migrations)

### `users`

| Column         | Type          | Constraints / default              | Description                   |
| -------------- | ------------- | ---------------------------------- | ----------------------------- |
| `id`           | `UUID`        | PK, `DEFAULT gen_random_uuid()`    | User identifier               |
| `google_id`    | `TEXT`        | `UNIQUE NOT NULL`                  | Subject (`sub`) from Google   |
| `email`        | `TEXT`        | `NOT NULL`                         | Email from the Google account |
| `display_name` | `TEXT`        | nullable                           | Display name                  |
| `streak_days`  | `INTEGER`     | `NOT NULL DEFAULT 0`               | Streak day count              |
| `created_at`   | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()`           | Creation timestamp            |

The insert on login is an **upsert** on `google_id` (updates `email` and
`display_name`).

### `collections`

| Column                | Type          | Constraints / default                               | Description                            |
| --------------------- | ------------- | --------------------------------------------------- | -------------------------------------- |
| `id`                  | `UUID`        | PK, `DEFAULT gen_random_uuid()`                     | Collection identifier                  |
| `user_id`             | `UUID`        | `NOT NULL`, FK → `users(id) ON DELETE CASCADE`      | Owner                                  |
| `name`                | `TEXT`        | `NOT NULL`                                          | Collection name (non-blank — validated) |
| `description`         | `TEXT`        | `NOT NULL DEFAULT ''`                               | Description                            |
| `source_language`     | `TEXT`        | `NOT NULL DEFAULT 'pl'`                             | Source language (`pl`,`en`,`de`,`es`,`fr`,`it`) |
| `target_language`     | `TEXT`        | `NOT NULL DEFAULT 'en'`                             | Target language; source ≠ target       |
| `last_studied`        | `TIMESTAMPTZ` | nullable                                            | Last learning session                  |
| `total_study_minutes` | `INTEGER`     | `NOT NULL DEFAULT 0`                                | Total study time in minutes            |
| `created_at`          | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()`                            | Creation timestamp                     |

> `flashcard_count` (number of flashcards) is **not a column** — the backend
> computes it in the query as `SELECT COUNT(*) ... AS flashcard_count`.
>
> `progress` (learning progress 0.0–1.0) is also **not a column** (dropped in
> migration 011) — the backend computes it per query as the average decayed SRS
> level of the collection's flashcards (`AVG` of the same exp-decay formula the
> client uses in `FlashcardDto.decayLevel`).

Language-code validation happens in the backend (422 on invalid/identical codes).

### `flashcards`

| Column            | Type          | Constraints / default                                  | Description                       |
| ----------------- | ------------- | ------------------------------------------------------ | --------------------------------- |
| `id`              | `UUID`        | PK, `DEFAULT gen_random_uuid()`                        | Flashcard identifier              |
| `collection_id`   | `UUID`        | `NOT NULL`, FK → `collections(id) ON DELETE CASCADE`   | Collection                        |
| `source_text`     | `TEXT`        | `NOT NULL`                                             | Source text (formerly `polish_text`) |
| `target_text`     | `TEXT`        | `NOT NULL`                                             | Target text (formerly `english_text`) |
| `position`        | `INT`         | `NOT NULL DEFAULT 0`                                   | Order within the collection (`max+1` on insert) |
| `srs_level`       | `REAL`        | `NOT NULL DEFAULT 0.0`                                 | Spaced-repetition level           |
| `last_studied_at` | `TIMESTAMPTZ` | nullable, `DEFAULT NULL`                               | Last review of this flashcard     |
| `created_at`      | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()`                               | Creation timestamp                |

> **Note:** the text columns are `source_text` / `target_text`. The old names
> `polish_text` / `english_text` (before migration 007) must not be used.

## Indexes

| Index                          | Table         | Column          |
| ------------------------------ | ------------- | --------------- |
| `idx_collections_user_id`      | `collections` | `user_id`       |
| `idx_flashcards_collection_id` | `flashcards`  | `collection_id` |

## Relationships

```
users (1) ──< collections (N) ──< flashcards (N)
        ON DELETE CASCADE        ON DELETE CASCADE
```

Deleting a user removes their collections; deleting a collection removes its flashcards.
