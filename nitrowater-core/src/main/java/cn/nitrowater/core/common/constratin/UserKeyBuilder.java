package cn.nitrowater.core.common.constratin;

import cn.nitrowater.core.common.RedisKeyPrefix;
import cn.nitrowater.core.common.cache.RedisKeyBuilder;

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
