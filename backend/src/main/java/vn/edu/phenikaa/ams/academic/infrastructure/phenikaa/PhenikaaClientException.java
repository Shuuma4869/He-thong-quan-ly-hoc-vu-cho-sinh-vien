package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

public final class PhenikaaClientException extends RuntimeException {
    public enum Code {
        SESSION_EXPIRED, HTTP_ERROR, BUSINESS_FAILURE, UNEXPECTED_SCHEMA,
        DECODE_ERROR, NETWORK_ERROR, TIMEOUT, RESPONSE_TOO_LARGE
    }

    private final Code code;

    public PhenikaaClientException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() { return code; }
    public boolean reconnectionRequired() { return code == Code.SESSION_EXPIRED; }
}
