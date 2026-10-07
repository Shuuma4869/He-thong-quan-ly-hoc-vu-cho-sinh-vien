ALTER TABLE study_plan_course ADD COLUMN scenario_no INTEGER NOT NULL DEFAULT 1;
ALTER TABLE study_plan_course ALTER COLUMN scenario_no DROP DEFAULT;
ALTER TABLE study_plan_course ADD CONSTRAINT study_plan_course_scenario_range
    CHECK (scenario_no BETWEEN 1 AND 5);

ALTER TABLE study_plan_course DROP CONSTRAINT study_plan_course_unique;
ALTER TABLE study_plan_course ADD CONSTRAINT study_plan_course_unique
    UNIQUE (profile_id, curriculum_id, scenario_no, course_id);

DROP INDEX study_plan_course_term_idx;
CREATE INDEX study_plan_course_term_idx
    ON study_plan_course(profile_id, curriculum_id, scenario_no, planned_term);
