package cn.nitrowater.lib.common.cache;

import cn.nitrowater.lib.common.RedisKeyPrefix;

public final class RedisKeyBuilder {
    
    private RedisKeyBuilder() {
    }

    /**
     * Builds a Redis key by joining {@code segments} with {@code ':'} and
     * prepending the global ROOT namespace ({@code nitrowater:biz:}).
     * <p>All business Redis keys MUST go through this method so that the ROOT
     * prefix is applied uniformly, separating business keys from {@code nitrowater:cache:*}.</p>
     */
    public static String build(Object... segments) {
        if (segments == null || segments.length == 0) return "";
        StringBuilder sb = new StringBuilder(64);
        sb.append(RedisKeyPrefix.ROOT_BIZ);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append(':');
            if (segments[i] != null) sb.append(segments[i].toString());
        }
        return sb.toString();
    }
}
