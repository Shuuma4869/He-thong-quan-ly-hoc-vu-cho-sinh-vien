CREATE TABLE phenikaa_connection (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    status VARCHAR(32) NOT NULL,
    encrypted_session BYTEA,
    encrypted_subject BYTEA NOT NULL,
    encryption_key_version INTEGER NOT NULL CHECK (encryption_key_version > 0),
    session_expires_at TIMESTAMPTZ,
    last_authenticated_at TIMESTAMPTZ NOT NULL,
    last_successful_access_at TIMESTAMPTZ,
    last_failed_access_at TIMESTAMPTZ,
    last_failure_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT phenikaa_connection_status CHECK (status IN ('CONNECTED', 'RECONNECTION_REQUIRED', 'DISCONNECTED')),
    CONSTRAINT phenikaa_connection_session CHECK (
        (status = 'DISCONNECTED' AND encrypted_session IS NULL AND session_expires_at IS NULL)
        OR (status IN ('CONNECTED', 'RECONNECTION_REQUIRED') AND encrypted_session IS NOT NULL
            AND octet_length(encrypted_session) BETWEEN 29 AND 200028)
    ),
    CONSTRAINT phenikaa_connection_subject CHECK (octet_length(encrypted_subject) BETWEEN 29 AND 1052),
    CONSTRAINT phenikaa_connection_failure CHECK (
        (last_failed_access_at IS NULL AND last_failure_code IS NULL)
        OR (last_failed_access_at IS NOT NULL AND last_failure_code IS NOT NULL AND last_failure_code IN (
            'SESSION_EXPIRED', 'HTTP_ERROR', 'BUSINESS_FAILURE', 'UNEXPECTED_SCHEMA',
            'DECODE_ERROR', 'NETWORK_ERROR', 'TIMEOUT', 'RESPONSE_TOO_LARGE', 'SESSION_INTEGRITY_FAILURE'))
    ),
    CONSTRAINT phenikaa_connection_timestamps CHECK (updated_at >= created_at AND last_authenticated_at >= created_at)
);
