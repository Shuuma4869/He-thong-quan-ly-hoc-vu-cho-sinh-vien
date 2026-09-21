package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.Arrays;

public final class PhenikaaSession implements AutoCloseable {
    private final char[] authorization;
    private final char[] cookie;
    private final char[] responseKey;
    private final char[] learnerId;
    private final char[] functionId;
    private boolean closed;

    public PhenikaaSession(String authorization, String cookie, String responseKey, String learnerId, String functionId) {
        validate(authorization, 16384, true);
        validate(cookie, 32768, true);
        validate(responseKey, 1024, false);
        validate(learnerId, 256, false);
        validate(functionId, 256, false);
        if (!authorization.isEmpty() && !authorization.startsWith("Bearer "))
            throw new IllegalArgumentException("Invalid authorization scheme");
        this.authorization = authorization.toCharArray();
        this.cookie = cookie.toCharArray();
        this.responseKey = responseKey.toCharArray();
        this.learnerId = learnerId.toCharArray();
        this.functionId = functionId.toCharArray();
    }

    private static void validate(String value, int limit, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isBlank()) || value.length() > limit
                || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Invalid session material");
    }

    private synchronized String value(char[] value) {
        if (closed) throw new IllegalStateException("Session is closed");
        return new String(value);
    }

    String authorization() { return value(authorization); }
    String cookie() { return value(cookie); }
    String responseKey() { return value(responseKey); }
    String learnerId() { return value(learnerId); }
    String functionId() { return value(functionId); }

    @Override public String toString() { return "PhenikaaSession[redacted]"; }

    @Override public synchronized void close() {
        for (char[] field : new char[][]{authorization, cookie, responseKey, learnerId, functionId}) Arrays.fill(field, '\0');
        closed = true;
    }
}
