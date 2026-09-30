package vn.edu.phenikaa.ams.sync.application;

public final class SyncCommandException extends RuntimeException {
    public enum Code {
        CONNECTION_NOT_FOUND, RECONNECTION_REQUIRED, RATE_LIMITED, ACCOUNT_UNAVAILABLE,
        RUN_NOT_FOUND, QUEUE_UNAVAILABLE, INVALID_HISTORY_LIMIT, INVALID_HISTORY_CURSOR
    }
    private final Code code;
    public SyncCommandException(Code code) { super(code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
