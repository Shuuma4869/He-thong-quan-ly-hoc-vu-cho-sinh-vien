CREATE TABLE academic_snapshot (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    source_system VARCHAR(80) NOT NULL CHECK (btrim(source_system) <> ''),
    captured_at TIMESTAMPTZ NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('COMPLETE', 'PARTIAL', 'FAILED')),
    content_hash VARCHAR(64) CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    UNIQUE (profile_id, id),
    CHECK (status = 'FAILED' OR content_hash IS NOT NULL)
);
CREATE INDEX academic_snapshot_capture_idx ON academic_snapshot(profile_id, source_system, captured_at DESC);

CREATE TABLE schedule_change (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    previous_snapshot_id UUID,
    current_snapshot_id UUID NOT NULL,
    class_session_id UUID,
    exam_id UUID,
    change_type VARCHAR(32) NOT NULL,
    before_values JSONB NOT NULL CHECK (jsonb_typeof(before_values) = 'object'),
    after_values JSONB NOT NULL CHECK (jsonb_typeof(after_values) = 'object'),
    detected_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (profile_id, previous_snapshot_id) REFERENCES academic_snapshot(profile_id, id),
    FOREIGN KEY (profile_id, current_snapshot_id) REFERENCES academic_snapshot(profile_id, id),
    FOREIGN KEY (profile_id, class_session_id) REFERENCES class_session(profile_id, id),
    FOREIGN KEY (profile_id, exam_id) REFERENCES exam(profile_id, id),
    CHECK (previous_snapshot_id IS NULL OR previous_snapshot_id <> current_snapshot_id),
    CHECK ((class_session_id IS NOT NULL AND exam_id IS NULL AND change_type IN
           ('CLASS_ADDED', 'CLASS_REMOVED', 'CLASS_CANCELLED', 'DATE_CHANGED', 'TIME_CHANGED', 'ROOM_CHANGED', 'LECTURER_CHANGED', 'MULTIPLE_FIELDS_CHANGED')) OR
           (exam_id IS NOT NULL AND class_session_id IS NULL AND change_type IN
           ('EXAM_ADDED', 'EXAM_CANCELLED', 'EXAM_DATE_CHANGED', 'EXAM_TIME_CHANGED', 'EXAM_ROOM_CHANGED', 'MULTIPLE_FIELDS_CHANGED')))
);
CREATE UNIQUE INDEX schedule_change_session_dedup_idx ON schedule_change(profile_id, current_snapshot_id, class_session_id, change_type) WHERE class_session_id IS NOT NULL;
CREATE UNIQUE INDEX schedule_change_exam_dedup_idx ON schedule_change(profile_id, current_snapshot_id, exam_id, change_type) WHERE exam_id IS NOT NULL;
CREATE INDEX schedule_change_previous_idx ON schedule_change(profile_id, previous_snapshot_id);
CREATE INDEX schedule_change_session_idx ON schedule_change(profile_id, class_session_id);
CREATE INDEX schedule_change_exam_idx ON schedule_change(profile_id, exam_id);
CREATE INDEX schedule_change_time_idx ON schedule_change(profile_id, detected_at DESC);
