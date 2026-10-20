ALTER TABLE app_user
    ADD COLUMN session_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN preserved_session_ref VARCHAR(64),
    ADD CONSTRAINT app_user_session_generation_check CHECK (session_generation >= 0),
    ADD CONSTRAINT app_user_preserved_session_ref_check
        CHECK (preserved_session_ref IS NULL OR preserved_session_ref ~ '^[0-9a-f]{64}$');
