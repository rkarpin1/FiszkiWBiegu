ALTER TABLE collections
    ADD COLUMN source_language TEXT NOT NULL DEFAULT 'pl',
    ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en';
