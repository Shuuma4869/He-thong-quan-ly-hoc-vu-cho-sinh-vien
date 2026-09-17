CREATE TABLE class_section (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    semester_id UUID NOT NULL,
    course_id UUID NOT NULL,
    section_code VARCHAR(80) NOT NULL CHECK (btrim(section_code) <> ''),
    FOREIGN KEY (profile_id, semester_id) REFERENCES semester(profile_id, id),
    FOREIGN KEY (profile_id, course_id) REFERENCES course(profile_id, id),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, semester_id, course_id, id),
    UNIQUE (profile_id, semester_id, course_id, section_code)
);
CREATE INDEX class_section_course_idx ON class_section(profile_id, course_id);

CREATE TABLE student_course (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    course_id UUID NOT NULL,
    semester_id UUID NOT NULL,
    section_id UUID,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    credits_attempted NUMERIC(5,2) NOT NULL CHECK (credits_attempted >= 0),
    FOREIGN KEY (profile_id, course_id) REFERENCES course(profile_id, id),
    FOREIGN KEY (profile_id, semester_id) REFERENCES semester(profile_id, id),
    FOREIGN KEY (profile_id, semester_id, course_id, section_id) REFERENCES class_section(profile_id, semester_id, course_id, id),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, id, credits_attempted),
    UNIQUE (profile_id, course_id, attempt_number)
);
CREATE INDEX student_course_semester_idx ON student_course(profile_id, semester_id);
CREATE INDEX student_course_section_idx ON student_course(profile_id, semester_id, course_id, section_id);

CREATE TABLE academic_result (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    student_course_id UUID NOT NULL,
    grading_policy_id UUID,
    status VARCHAR(16) NOT NULL CHECK (status IN ('IN_PROGRESS', 'PASSED', 'FAILED', 'WITHDRAWN', 'EXEMPTED')),
    numeric_score NUMERIC(6,2) CHECK (numeric_score >= 0),
    letter_grade VARCHAR(16),
    grade_points NUMERIC(6,2) CHECK (grade_points >= 0),
    credits_attempted NUMERIC(5,2) NOT NULL,
    credits_earned NUMERIC(5,2) NOT NULL CHECK (credits_earned >= 0 AND credits_earned <= credits_attempted),
    included_in_gpa BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (profile_id, student_course_id, credits_attempted) REFERENCES student_course(profile_id, id, credits_attempted),
    FOREIGN KEY (profile_id, grading_policy_id) REFERENCES grading_policy(profile_id, id),
    UNIQUE (profile_id, student_course_id),
    CHECK (status IN ('PASSED', 'EXEMPTED') OR credits_earned = 0),
    CHECK (NOT included_in_gpa OR (status IN ('PASSED', 'FAILED') AND grade_points IS NOT NULL AND grading_policy_id IS NOT NULL))
);
CREATE INDEX academic_result_policy_idx ON academic_result(profile_id, grading_policy_id);
CREATE INDEX academic_result_status_idx ON academic_result(profile_id, status);

CREATE TABLE class_session (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    section_id UUID NOT NULL,
    occurrence_key VARCHAR(80) NOT NULL CHECK (btrim(occurrence_key) <> ''),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    room VARCHAR(160),
    lecturer VARCHAR(200),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (profile_id, section_id) REFERENCES class_section(profile_id, id),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, section_id, occurrence_key)
);
CREATE INDEX class_session_time_idx ON class_session(profile_id, starts_at);

CREATE TABLE exam (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    student_course_id UUID NOT NULL,
    occurrence_key VARCHAR(80) NOT NULL CHECK (btrim(occurrence_key) <> ''),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ CHECK (ends_at > starts_at),
    room VARCHAR(160),
    format VARCHAR(80),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (profile_id, student_course_id) REFERENCES student_course(profile_id, id),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, student_course_id, occurrence_key)
);
CREATE INDEX exam_time_idx ON exam(profile_id, starts_at);
