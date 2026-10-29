package cn.nitrowater.server.infra.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed rate limiting primitives used by the sandbox and assistant endpoints.
 *
 * <p>Two mechanisms: a sliding-window counter for per-minute quotas and a fixed-window counter for
 * per-day quotas. Both carry a TTL so a crashed or idle key cannot leak capacity. Assistant token
 * quotas are separate: they are metered in tokens by {@code AiUsageService}.</p>
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
     * @param key the window key (one per user / scope)
     * @param limitPerMinute maximum accepted requests within the trailing 60 seconds
     * @return {@code true} if the request is admitted; {@code false} if the cap is reached
     */
    public boolean allowSliding(String key, int limitPerMinute) {
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
     * Fixed-window admission check: increments the counter and fails once the cap is exceeded.
     *
     * @param key the counter key (one per user / day)
     * @param limit maximum accepted requests within the window
     * @param ttl how long the counter survives after its first hit
     * @return {@code true} if the request is admitted; {@code false} if the cap is reached
     */
    public boolean allowFixed(String key, int limit, Duration ttl) {
        Long current = redis.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redis.expire(key, ttl);
        }
        return current != null && current <= limit;
    }
}
