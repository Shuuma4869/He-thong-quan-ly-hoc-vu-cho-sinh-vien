package vn.edu.phenikaa.ams.academic.application;

public final class CurriculumImportException extends RuntimeException {
    public enum Code { PROFILE_REQUIRED, INVALID_OBSERVATION, IDENTITY_CONFLICT, CREDIT_CONFLICT, GROUP_CONFLICT }
    public CurriculumImportException(Code code) { super(code.name(), null, false, false); }
}
