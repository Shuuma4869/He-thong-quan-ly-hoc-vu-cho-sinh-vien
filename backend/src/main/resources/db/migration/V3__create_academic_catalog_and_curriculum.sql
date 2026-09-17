CREATE TABLE student_profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    student_number VARCHAR(80),
    institution_name VARCHAR(200),
    program_name VARCHAR(200),
    cohort VARCHAR(40),
    curriculum_id UUID,
    grading_policy_id UUID
);

CREATE TABLE semester (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    academic_year_start INTEGER NOT NULL CHECK (academic_year_start BETWEEN 1900 AND 9998),
    term_code VARCHAR(16) NOT NULL CHECK (term_code ~ '^[A-Z0-9][A-Z0-9_-]{0,15}$'),
    name VARCHAR(160) NOT NULL CHECK (btrim(name) <> ''),
    starts_on DATE,
    ends_on DATE,
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, academic_year_start, term_code),
    CHECK ((starts_on IS NULL AND ends_on IS NULL) OR
           (starts_on IS NOT NULL AND ends_on IS NOT NULL AND ends_on >= starts_on))
);

CREATE TABLE course (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    code VARCHAR(40) NOT NULL CHECK (code = upper(btrim(code)) AND code <> ''),
    name VARCHAR(240) NOT NULL CHECK (btrim(name) <> ''),
    credits NUMERIC(5,2) NOT NULL CHECK (credits >= 0),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, code)
);

CREATE TABLE grading_policy (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    code VARCHAR(40) NOT NULL CHECK (btrim(code) <> ''),
    revision VARCHAR(40) NOT NULL CHECK (btrim(revision) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    max_numeric_score NUMERIC(6,2) NOT NULL CHECK (max_numeric_score > 0),
    max_grade_points NUMERIC(6,2) NOT NULL CHECK (max_grade_points > 0),
    repeat_strategy VARCHAR(16) NOT NULL CHECK (repeat_strategy IN ('LATEST', 'HIGHEST', 'ALL')),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, code, revision)
);

CREATE TABLE grading_classification (
    policy_id UUID NOT NULL REFERENCES grading_policy(id),
    label VARCHAR(80) NOT NULL CHECK (btrim(label) <> ''),
    minimum_gpa NUMERIC(6,2) NOT NULL CHECK (minimum_gpa >= 0),
    PRIMARY KEY (policy_id, label),
    UNIQUE (policy_id, minimum_gpa)
);

CREATE TABLE curriculum (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    code VARCHAR(40) NOT NULL CHECK (btrim(code) <> ''),
    revision VARCHAR(40) NOT NULL CHECK (btrim(revision) <> ''),
    name VARCHAR(240) NOT NULL CHECK (btrim(name) <> ''),
    cohort VARCHAR(40),
    minimum_credits NUMERIC(6,2) NOT NULL CHECK (minimum_credits >= 0),
    UNIQUE (profile_id, id),
    UNIQUE (profile_id, code, revision)
);

CREATE TABLE curriculum_group (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    curriculum_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL CHECK (btrim(code) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    minimum_credits NUMERIC(6,2) NOT NULL CHECK (minimum_credits >= 0),
    FOREIGN KEY (profile_id, curriculum_id) REFERENCES curriculum(profile_id, id),
    UNIQUE (profile_id, curriculum_id, id),
    UNIQUE (profile_id, curriculum_id, code)
);

CREATE TABLE curriculum_course (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    curriculum_id UUID NOT NULL,
    course_id UUID NOT NULL,
    requirement VARCHAR(16) NOT NULL CHECK (requirement IN ('REQUIRED', 'ELECTIVE')),
    group_id UUID,
    credits NUMERIC(5,2) NOT NULL CHECK (credits >= 0),
    recommended_term INTEGER CHECK (recommended_term > 0),
    FOREIGN KEY (profile_id, curriculum_id) REFERENCES curriculum(profile_id, id),
    FOREIGN KEY (profile_id, course_id) REFERENCES course(profile_id, id),
    FOREIGN KEY (profile_id, curriculum_id, group_id) REFERENCES curriculum_group(profile_id, curriculum_id, id),
    UNIQUE (profile_id, curriculum_id, course_id),
    CHECK ((requirement = 'REQUIRED' AND group_id IS NULL) OR (requirement = 'ELECTIVE' AND group_id IS NOT NULL))
);
CREATE INDEX curriculum_course_course_idx ON curriculum_course(profile_id, course_id);
CREATE INDEX curriculum_course_group_idx ON curriculum_course(profile_id, curriculum_id, group_id);

CREATE TABLE course_prerequisite (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES student_profile(id),
    curriculum_id UUID NOT NULL,
    course_id UUID NOT NULL,
    prerequisite_course_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('PREREQUISITE', 'COREQUISITE')),
    FOREIGN KEY (profile_id, curriculum_id, course_id) REFERENCES curriculum_course(profile_id, curriculum_id, course_id),
    FOREIGN KEY (profile_id, curriculum_id, prerequisite_course_id) REFERENCES curriculum_course(profile_id, curriculum_id, course_id),
    UNIQUE (profile_id, curriculum_id, course_id, prerequisite_course_id),
    CHECK (course_id <> prerequisite_course_id)
);
CREATE INDEX course_prerequisite_reverse_idx ON course_prerequisite(profile_id, curriculum_id, prerequisite_course_id);

ALTER TABLE student_profile ADD FOREIGN KEY (id, curriculum_id) REFERENCES curriculum(profile_id, id);
ALTER TABLE student_profile ADD FOREIGN KEY (id, grading_policy_id) REFERENCES grading_policy(profile_id, id);
