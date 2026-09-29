package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.edu.phenikaa.ams.academic.application.port.AcademicReadGate;

public final class RedisAcademicReadGate implements AcademicReadGate {
    private final StringRedisTemplate redis;
    private final Duration cooldown;

    public RedisAcademicReadGate(StringRedisTemplate redis, Duration cooldown) {
        if (cooldown.compareTo(Duration.ofSeconds(1)) < 0 || cooldown.compareTo(Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Read cooldown must be between 1 second and 1 minute");
        this.redis = redis;
        this.cooldown = cooldown;
    }

    @Override public boolean tryAcquire(UUID userId, Capability capability) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                "ams:phenikaa:read:" + userId + ":" + capability.name(), "1", cooldown));
    }
}
