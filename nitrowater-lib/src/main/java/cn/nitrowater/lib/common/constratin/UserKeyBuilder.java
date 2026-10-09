package cn.nitrowater.lib.common.constratin;

import cn.nitrowater.lib.common.RedisKeyPrefix;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;

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
