package vn.edu.phenikaa.ams.calendar.google;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;

final class GoogleOAuthStateStore {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "ams:oauth:google-calendar:";
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    GoogleOAuthStateStore(StringRedisTemplate redis) { this.redis = redis; }

    Started start(UUID userId, long expectedVersion) {
        byte[] stateBytes = new byte[32];
        byte[] verifierBytes = new byte[32];
        random.nextBytes(stateBytes);
        random.nextBytes(verifierBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String value = userId + ":" + expectedVersion + ":" + verifier;
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + state, value, TTL)))
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        } catch (DataAccessException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        }
        return new Started(state, verifier, challenge(verifier));
    }

    Consumed consume(String state, UUID userId) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}"))
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);
        String value;
        try { value = redis.opsForValue().getAndDelete(PREFIX + state); }
        catch (DataAccessException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        }
        if (value == null) throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);
        String[] fields = value.split(":", -1);
        try {
            if (fields.length != 3 || !UUID.fromString(fields[0]).equals(userId)
                    || !fields[2].matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException();
            return new Consumed(Long.parseLong(fields[1]), fields[2]);
        } catch (IllegalArgumentException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);
        }
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    record Started(String state, String verifier, String challenge) {
        @Override public String toString() { return "GoogleOAuthStarted[redacted]"; }
    }
    record Consumed(long expectedVersion, String verifier) {
        @Override public String toString() { return "GoogleOAuthConsumed[redacted]"; }
    }
}
