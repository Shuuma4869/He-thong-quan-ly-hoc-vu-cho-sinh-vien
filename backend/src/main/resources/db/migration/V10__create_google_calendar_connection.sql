CREATE TABLE google_calendar_connection (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('CONNECTED', 'SETUP_REQUIRED', 'RECONNECTION_REQUIRED', 'DISCONNECTED')),
    encrypted_tokens BYTEA,
    encryption_key_version INTEGER NOT NULL CHECK (encryption_key_version > 0),
    calendar_id VARCHAR(1024),
    token_expires_at TIMESTAMPTZ,
    connected_at TIMESTAMPTZ,
    last_successful_access_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT google_calendar_connection_tokens CHECK (
        (status IN ('CONNECTED', 'SETUP_REQUIRED') AND encrypted_tokens IS NOT NULL
            AND octet_length(encrypted_tokens) BETWEEN 29 AND 16412 AND token_expires_at IS NOT NULL)
        OR (status IN ('RECONNECTION_REQUIRED', 'DISCONNECTED') AND encrypted_tokens IS NULL
            AND token_expires_at IS NULL)
    ),
    CONSTRAINT google_calendar_connection_ready CHECK (
        status <> 'CONNECTED' OR (calendar_id IS NOT NULL AND connected_at IS NOT NULL)
    ),
    CONSTRAINT google_calendar_connection_timestamps CHECK (updated_at >= created_at)
);
