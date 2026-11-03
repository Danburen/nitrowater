package cn.nitrowater.resource.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Redis cache for presigned read URLs (aligned with waterfun's read-cache pattern).
 *
 * <p>A presigned GET URL is cached under {@code nitrowater:cache:resource:read:{uuid}} for
 * {@code presignExpiry - safetyMargin} seconds, so repeated reads reuse the same signed URL instead
 * of re-signing (and re-hitting the storage backend) on every request. The safety margin guarantees
 * a cache hit never returns a URL that is about to expire.</p>
 *
 * <p>Redis is treated as a best-effort accelerator: if it is unavailable the caller transparently
 * falls back to signing on every request rather than failing the read.</p>
 */
@Component
public class PresignedUrlCache {

    private static final String KEY_PREFIX = "nitrowater:cache:resource:read:";

    private final StringRedisTemplate redis;

    public PresignedUrlCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Returns the cached presigned URL for a resource, or signs a fresh one and caches it.
     *
     * @param uuid resource identifier (the cache key)
     * @param presignExpiry full lifetime of a freshly-signed URL
     * @param safetyMarginSeconds seconds subtracted from {@code presignExpiry} for the cache TTL
     * @param signer produces a fresh presigned URL; may return {@code null} when the backend does not
     *        support presigning (in which case nothing is cached and {@code null} is returned)
     * @return the (possibly cached) presigned URL, or {@code null}
     */
    public String getOrSign(String uuid, Duration presignExpiry, int safetyMarginSeconds, Supplier<String> signer) {
        String key = key(uuid);
        String cached = getQuietly(key);
        if (StringUtil.isNotBlank(cached)) {
            return cached;
        }
        String url = signer.get();
        if (url == null) {
            return null;
        }
        Duration ttl = presignExpiry.minusSeconds(safetyMarginSeconds);
        if (ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofSeconds(1);
        }
        setQuietly(key, url, ttl);
        return url;
    }

    /**
     * Evicts the cached URL for a resource (call when the resource is deleted/changed).
     *
     * @param uuid resource identifier
     */
    public void evict(String uuid) {
        try {
            redis.delete(key(uuid));
        } catch (Exception ignored) {
            // Redis is a best-effort accelerator; eviction failures must not break the operation.
        }
    }

    private String getQuietly(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setQuietly(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
            // Cache write failures degrade to per-request signing.
        }
    }

    private static String key(String uuid) {
        return KEY_PREFIX + uuid;
    }
}
