package vn.edu.phenikaa.ams.calendar.google;

public final class GoogleCalendarException extends RuntimeException {
    public enum Code {
        GOOGLE_NOT_CONFIGURED, GOOGLE_STATE_INVALID, GOOGLE_AUTH_DENIED,
        GOOGLE_TOKEN_EXCHANGE_FAILED, GOOGLE_RECONNECTION_REQUIRED,
        GOOGLE_CALENDAR_SETUP_FAILED, GOOGLE_UNAVAILABLE
    }

    private final Code code;
    public GoogleCalendarException(Code code) {
        super(code.name());
        this.code = code;
    }
    public Code code() { return code; }
}
