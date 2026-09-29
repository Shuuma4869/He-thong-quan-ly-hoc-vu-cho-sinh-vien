-- A source organization identifies a curriculum even when its published revision is unknown.
ALTER TABLE curriculum ALTER COLUMN revision DROP NOT NULL;

ALTER TABLE curriculum_group
    ADD COLUMN requirement VARCHAR(16) NOT NULL DEFAULT 'ELECTIVE',
    ADD COLUMN minimum_course_count INTEGER CHECK (minimum_course_count >= 0),
    ALTER COLUMN minimum_credits DROP NOT NULL,
    ADD CONSTRAINT curriculum_group_requirement CHECK (requirement IN ('REQUIRED', 'ELECTIVE')),
    ADD CONSTRAINT curriculum_group_elective_credits CHECK (requirement <> 'ELECTIVE' OR minimum_credits IS NOT NULL),
    ADD CONSTRAINT curriculum_group_requirement_identity UNIQUE (profile_id, curriculum_id, id, requirement);
ALTER TABLE curriculum_group ALTER COLUMN requirement DROP DEFAULT;

-- Locate the original anonymous check by its column references, without depending on PostgreSQL's generated name.
DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'curriculum_course'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%requirement%' AND pg_get_constraintdef(oid) LIKE '%group_id%'
    LOOP
        EXECUTE format('ALTER TABLE curriculum_course DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE curriculum_course
    ADD CONSTRAINT curriculum_course_elective_group CHECK (requirement <> 'ELECTIVE' OR group_id IS NOT NULL),
    ADD CONSTRAINT curriculum_course_group_requirement FOREIGN KEY (profile_id, curriculum_id, group_id, requirement)
        REFERENCES curriculum_group(profile_id, curriculum_id, id, requirement);

CREATE TABLE phenikaa_curriculum_mapping (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    source_curriculum_id VARCHAR(128) NOT NULL CHECK (btrim(source_curriculum_id) <> ''),
    curriculum_id UUID NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL CHECK (last_seen_at >= first_seen_at),
    FOREIGN KEY (profile_id, curriculum_id) REFERENCES curriculum(profile_id, id),
    UNIQUE (profile_id, source_curriculum_id),
    UNIQUE (profile_id, curriculum_id)
);

CREATE TABLE phenikaa_course_mapping (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    source_course_id VARCHAR(128) NOT NULL CHECK (btrim(source_course_id) <> ''),
    course_id UUID NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL CHECK (last_seen_at >= first_seen_at),
    FOREIGN KEY (profile_id, course_id) REFERENCES course(profile_id, id),
    UNIQUE (profile_id, source_course_id),
    UNIQUE (profile_id, course_id)
);

CREATE TABLE phenikaa_curriculum_group_mapping (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    curriculum_id UUID NOT NULL,
    source_group_id VARCHAR(128) NOT NULL CHECK (btrim(source_group_id) <> ''),
    group_id UUID NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL CHECK (last_seen_at >= first_seen_at),
    FOREIGN KEY (profile_id, curriculum_id) REFERENCES curriculum(profile_id, id),
    FOREIGN KEY (profile_id, curriculum_id, group_id) REFERENCES curriculum_group(profile_id, curriculum_id, id),
    UNIQUE (profile_id, curriculum_id, source_group_id),
    UNIQUE (profile_id, curriculum_id, group_id)
);
