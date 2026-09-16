CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT app_user_role_check CHECK (role IN ('STUDENT', 'ADMIN', 'SYSTEM'))
);

CREATE TABLE security_audit_event (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES app_user(id),
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX security_audit_event_user_time_idx
    ON security_audit_event (user_id, occurred_at DESC);
