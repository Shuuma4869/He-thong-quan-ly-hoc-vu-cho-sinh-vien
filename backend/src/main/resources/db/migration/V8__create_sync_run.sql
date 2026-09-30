CREATE TABLE sync_run (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    trigger_type VARCHAR(16) NOT NULL CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    current_step VARCHAR(16) CHECK (current_step IN ('PROFILE', 'CURRICULUM')),
    profile_step_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (profile_step_status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    curriculum_step_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (curriculum_step_status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    failure_code VARCHAR(40) CHECK (failure_code IN (
        'CONNECTION_NOT_FOUND', 'RECONNECTION_REQUIRED', 'SOURCE_TIMEOUT', 'SOURCE_UNAVAILABLE',
        'SOURCE_SCHEMA_CHANGED', 'PROFILE_REFRESH_FAILED', 'CURRICULUM_REFRESH_FAILED',
        'LOCK_UNAVAILABLE', 'INTERNAL_ERROR')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sync_run_lifecycle CHECK (
        (status = 'QUEUED' AND finished_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND heartbeat_at IS NOT NULL AND finished_at IS NULL)
        OR (status IN ('SUCCEEDED', 'PARTIAL', 'FAILED') AND finished_at IS NOT NULL)
    ),
    CONSTRAINT sync_run_outcome CHECK (
        (status <> 'SUCCEEDED' OR (profile_step_status = 'SUCCEEDED' AND curriculum_step_status = 'SUCCEEDED'))
        AND (status <> 'PARTIAL' OR (profile_step_status = 'SUCCEEDED' AND curriculum_step_status = 'FAILED'))
    )
);

CREATE UNIQUE INDEX sync_run_one_active_user ON sync_run(user_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX sync_run_ready ON sync_run(next_attempt_at, requested_at) WHERE status = 'QUEUED';
CREATE INDEX sync_run_stale ON sync_run(heartbeat_at) WHERE status = 'RUNNING';
CREATE INDEX sync_run_history ON sync_run(user_id, requested_at DESC);
