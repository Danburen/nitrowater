package cn.nitrowater.core.application.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.lib.common.RedisKeyPrefix;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared verification-code lifecycle: generate, deliver, store in Redis, then verify once.
 *
 * <p>Delivery is the only channel-specific part. Implementations must deliver before the code is
 * stored, so a provider failure never leaves a verifiable code that the user never received.</p>
 */
public abstract class AbstractRedisCodeSender implements CodeSender, CodeVerifier {

    private final RedisHelperHolder redisHelper;
    private final VerifyChannel channel;
    private final long expireMinutes;

    protected AbstractRedisCodeSender(RedisHelperHolder redisHelper, VerifyChannel channel,
            long expireMinutes) {
        this.redisHelper = redisHelper;
        this.channel = channel;
        this.expireMinutes = Math.max(1, expireMinutes);
    }

    @Override
    public CodeResult sendCode(String target, VerifyScene scene) {
        String code = String.valueOf(generateVerifyCode());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        deliver(target, scene, code, expireMinutes);
        redisHelper.set(codeKey(target, scene, uuid), code, Duration.ofMinutes(expireMinutes));
        return CodeResult.success(target, channel).withKey(uuid);
    }

    @Override
    public boolean verifyCode(String target, VerifyScene scene, String key, String code) {
        return redisHelper.validateAndRemove(codeKey(target, scene, key), code);
    }

    @Override
    public Object generateVerifyCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    @Override
    public VerifyChannel channel() {
        return channel;
    }

    /**
     * Delivers the code through the configured provider.
     *
     * @param target phone or email
     * @param scene verification scene
     * @param code six-digit code
     * @param expireMinutes code TTL
     */
    protected abstract void deliver(String target, VerifyScene scene, String code, long expireMinutes);

    private String codeKey(String target, VerifyScene scene, String key) {
        return RedisKeyBuilder.build(RedisKeyPrefix.VERIFY,
                channel.name().toLowerCase(Locale.ROOT), target, scene.getValue(), key);
    }
}
