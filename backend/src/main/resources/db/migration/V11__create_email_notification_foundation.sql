ALTER TABLE user_preferences
    ADD COLUMN notification_email_verified_at TIMESTAMPTZ,
    ADD COLUMN sync_email_alerts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT user_preferences_alert_requires_verification
        CHECK (NOT sync_email_alerts_enabled OR
            (notification_email IS NOT NULL AND notification_email_verified_at IS NOT NULL));

CREATE TABLE notification_email_verification (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    email_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    sync_run_id UUID NOT NULL REFERENCES sync_run(id) ON DELETE CASCADE,
    notification_type VARCHAR(32) NOT NULL CHECK (notification_type = 'SYNC_ALERT'),
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    text_body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'DEAD', 'SKIPPED')),
    attempt_count SMALLINT NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    provider_message_id VARCHAR(256),
    last_error_code VARCHAR(40),
    CONSTRAINT notification_outbox_sync_unique UNIQUE (notification_type, sync_run_id),
    CONSTRAINT notification_outbox_lease CHECK
        ((status = 'PROCESSING') = (lease_token IS NOT NULL AND lease_expires_at IS NOT NULL))
);

CREATE INDEX notification_outbox_due ON notification_outbox(status, next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX notification_outbox_expired_lease ON notification_outbox(lease_expires_at)
    WHERE status = 'PROCESSING';
CREATE INDEX notification_outbox_retention ON notification_outbox(updated_at, id)
    WHERE status IN ('SENT', 'DEAD', 'SKIPPED');
