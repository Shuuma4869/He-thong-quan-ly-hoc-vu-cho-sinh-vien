UPDATE app_user SET email = lower(btrim(email));
ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT '!',
    ADD COLUMN display_name VARCHAR(80),
    ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    DROP CONSTRAINT app_user_role_check,
    ADD CONSTRAINT app_user_role_check CHECK (role IN ('STUDENT', 'ADMIN')),
    ADD CONSTRAINT app_user_status_check CHECK (account_status IN ('ACTIVE', 'DISABLED')),
    ADD CONSTRAINT app_user_email_normalized_check CHECK (email = lower(btrim(email)));
ALTER TABLE app_user ALTER COLUMN password_hash DROP DEFAULT;
ALTER TABLE app_user ALTER COLUMN account_status DROP DEFAULT;

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    notification_email VARCHAR(254),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    locale VARCHAR(16) NOT NULL DEFAULT 'vi-VN',
    theme VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_preferences_theme_check CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT user_preferences_locale_check CHECK (locale IN ('vi-VN', 'en-US'))
);

INSERT INTO user_preferences (user_id) SELECT id FROM app_user;
