package cn.nitrowater.core.lib.common;

import cn.nitrowater.core.lib.common.cache.RedisKeyBuilder;

/**
 * A final Constants class for Redis key prefixes with root namespace propose.
 * @author huangcong
 */
public final class RedisKeyPrefix {

    public RedisKeyPrefix() {}

    /**
     * Global ROOT namespace for all business Redis keys, prepended by
     * {@link RedisKeyBuilder#build(Object...)}.
     * <p>Distinguishes business keys ({@code waterfun:biz:*}) from cache keys
     * ({@code waterfun:cache:*}) produced by Spring {@code @Cacheable}.</p>
     */
    public static final String ROOT_BIZ = "waterfun:biz:";
    public static final String ROOT_CACHE =  "waterfun:cache:";

    public static final String ONLINE = "online";
    public static final String THRESHOLD = "threshold";
    public static final String VERIFY = "verify";
    public static final String CLOUD = "cloud";
    public static final String UPLOADS = "uploads";
    public static final String USER = "user";
    public static final String REFRESH_TOKEN = "rt";
    public static final String RE_AUTH = "op:re-auth";
}
