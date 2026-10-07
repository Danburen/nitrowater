package cn.nitrowater.core.lib.common.constratin;

import cn.nitrowater.core.lib.common.RedisKeyPrefix;
import cn.nitrowater.core.lib.common.cache.RedisKeyBuilder;

public final class UserKeyBuilder {

    private UserKeyBuilder() {}

    public static String userAccessDevice(long userUid, String deviceId) {
        return RedisKeyBuilder.build(
                RedisKeyPrefix.USER,
                String.valueOf(userUid),
                "device",
                deviceId,
                "access"
        );
    }
}
