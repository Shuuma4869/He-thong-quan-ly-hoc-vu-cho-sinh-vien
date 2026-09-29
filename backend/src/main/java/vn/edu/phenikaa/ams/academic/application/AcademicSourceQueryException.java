package vn.edu.phenikaa.ams.academic.application;

public final class AcademicSourceQueryException extends RuntimeException {
    public enum Code {
        CONNECTION_NOT_FOUND, RECONNECTION_REQUIRED, SOURCE_UNAVAILABLE, SOURCE_TIMEOUT,
        SOURCE_SCHEMA_CHANGED, SOURCE_DATA_INCOMPLETE, INVALID_SOURCE_REFERENCE, RATE_LIMITED
    }

    private final Code code;

    public AcademicSourceQueryException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() { return code; }
}
