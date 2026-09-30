package vn.edu.phenikaa.ams.calendar.google;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;

final class GoogleOperationLock {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> RELEASE = releaseScript();
    private final StringRedisTemplate redis;

    GoogleOperationLock(StringRedisTemplate redis) { this.redis = redis; }

    Lease acquire(UUID userId, String operation) {
        if (!operation.equals("refresh") && !operation.equals("setup")) throw new IllegalArgumentException();
        String key = "ams:google-calendar:" + operation + ":" + userId;
        String owner = UUID.randomUUID().toString();
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, TTL))
                    ? new Lease(redis, key, owner) : null;
        } catch (DataAccessException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        }
    }

    static final class Lease implements AutoCloseable {
        private final StringRedisTemplate redis;
        private final String key;
        private final String owner;
        private Lease(StringRedisTemplate redis, String key, String owner) {
            this.redis = redis; this.key = key; this.owner = owner;
        }
        @Override public void close() {
            try { redis.execute(RELEASE, List.of(key), owner); }
            catch (DataAccessException ignored) { /* Lease expires after the bounded TTL. */ }
        }
        @Override public String toString() { return "GoogleOperationLease[redacted]"; }
    }

    private static DefaultRedisScript<Long> releaseScript() {
        var script = new DefaultRedisScript<Long>();
        script.setScriptText("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """);
        script.setResultType(Long.class);
        return script;
    }
}
