package vn.edu.phenikaa.ams.academic.application;

public final class StudyPlanException extends RuntimeException {
    public enum Code {
        CURRICULUM_SELECTION_REQUIRED, STUDY_PLAN_SELECTION_CHANGED, STUDY_PLAN_COURSE_NOT_FOUND,
        INVALID_PLANNED_TERM, INVALID_STUDY_PLAN_SCENARIO, STUDY_PLAN_SCENARIO_NOT_EMPTY,
        STUDY_PLAN_TOO_LARGE
    }

    private final Code code;
    public StudyPlanException(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
