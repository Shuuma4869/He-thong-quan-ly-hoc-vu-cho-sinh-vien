package vn.edu.phenikaa.ams.sync.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisSyncLock {
    private static final DefaultRedisScript<Long> RENEW = script("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> RELEASE = script("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """);
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisSyncLock(StringRedisTemplate redis, Duration ttl) {
        if (ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("Sync lock TTL must be 1 second to 10 minutes");
        this.redis = redis;
        this.ttl = ttl;
    }

    public String acquire(UUID userId) {
        String owner = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(userId), owner, ttl)) ? owner : null;
    }

    public boolean renew(UUID userId, String owner) {
        return Long.valueOf(1).equals(redis.execute(RENEW, List.of(key(userId)), owner, Long.toString(ttl.toMillis())));
    }

    public boolean release(UUID userId, String owner) {
        return Long.valueOf(1).equals(redis.execute(RELEASE, List.of(key(userId)), owner));
    }

    public boolean locked(UUID userId) { return Boolean.TRUE.equals(redis.hasKey(key(userId))); }
    public Duration ttl() { return ttl; }
    private static String key(UUID userId) { return "ams:sync:user:" + userId; }
    private static DefaultRedisScript<Long> script(String code) {
        var script = new DefaultRedisScript<Long>();
        script.setScriptText(code);
        script.setResultType(Long.class);
        return script;
    }
}
