CREATE TABLE study_plan_course (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    curriculum_id UUID NOT NULL,
    course_id UUID NOT NULL,
    planned_term INTEGER NOT NULL CHECK (planned_term BETWEEN 1 AND 99),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT study_plan_course_time_order CHECK (updated_at >= created_at),
    CONSTRAINT study_plan_course_membership FOREIGN KEY (profile_id, curriculum_id, course_id)
        REFERENCES curriculum_course(profile_id, curriculum_id, course_id),
    CONSTRAINT study_plan_course_unique UNIQUE (profile_id, curriculum_id, course_id)
);

CREATE INDEX study_plan_course_term_idx
    ON study_plan_course(profile_id, curriculum_id, planned_term);
