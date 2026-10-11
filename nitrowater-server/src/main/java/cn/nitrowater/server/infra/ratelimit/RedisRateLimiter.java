package cn.nitrowater.server.infra.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed rate limiting primitives used by the sandbox endpoints.
 *
 * <p>Two mechanisms: a sliding-window request counter keyed by client IP, and a global in-flight
 * counter acting as a concurrency gate. Both use TTLs so a crashed holder cannot leak capacity.</p>
 */
@Component
public class RedisRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Sliding-window admission check: records this request if under the per-minute cap.
     *
     * @param key the window key (one per client IP / scope)
     * @param limitPerMinute maximum accepted requests within the trailing 60 seconds
     * @return {@code true} if the request is admitted; {@code false} if the cap is reached
     */
    public boolean allow(String key, int limitPerMinute) {
        long now = System.currentTimeMillis();
        redis.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MS);
        Long count = redis.opsForZSet().zCard(key);
        if (count != null && count >= limitPerMinute) {
            return false;
        }
        redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
        redis.expire(key, Duration.ofSeconds(120));
        return true;
    }

    /**
     * Acquires one global concurrency slot, or fails when {@code max} are already in flight.
     *
     * @param key the shared slot counter key
     * @param max maximum concurrent holders
     * @return {@code true} if a slot was taken; {@code false} if the gate is full
     */
    public boolean acquire(String key, int max) {
        Long current = redis.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        if (current != null && current > max) {
            redis.opsForValue().decrement(key);
            return false;
        }
        return true;
    }

    /** Releases a previously acquired concurrency slot. */
    public void release(String key) {
        Long current = redis.opsForValue().decrement(key);
        if (current != null && current < 0) {
            redis.opsForValue().set(key, "0");
        }
    }
}
