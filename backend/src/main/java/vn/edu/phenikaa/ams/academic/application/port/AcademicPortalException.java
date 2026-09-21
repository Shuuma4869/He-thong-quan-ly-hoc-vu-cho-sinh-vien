package vn.edu.phenikaa.ams.academic.application.port;

import java.util.Objects;

public final class AcademicPortalException extends RuntimeException {
    public enum Code {
        CONNECTION_UNAVAILABLE, SOURCE_ACCOUNT_MISMATCH, SESSION_INTEGRITY_FAILURE,
        SESSION_EXPIRED, HTTP_ERROR, BUSINESS_FAILURE, UNEXPECTED_SCHEMA,
        DECODE_ERROR, NETWORK_ERROR, TIMEOUT, RESPONSE_TOO_LARGE
    }
    private final Code code;
    public AcademicPortalException(Code code) { super(Objects.requireNonNull(code).name()); this.code = code; }
    public Code code() { return code; }
}
