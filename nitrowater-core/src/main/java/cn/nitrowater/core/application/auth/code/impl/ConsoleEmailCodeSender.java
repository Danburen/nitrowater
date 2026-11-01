package cn.nitrowater.core.application.auth.code.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.lib.utils.MaskUtil;

/**
 * Local-development email sender. Logs the code instead of delivering it.
 *
 * <p>Selected when {@code app.communication.mode=mock} (the default). Never use this mode in
 * production: verification codes appear in application logs.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode",
        havingValue = "mock", matchIfMissing = true)
public class ConsoleEmailCodeSender extends AbstractRedisCodeSender {

    public ConsoleEmailCodeSender(RedisHelperHolder redisHelper,
            @Value("${expire.email.verify:5}") long expireMinutes) {
        super(redisHelper, VerifyChannel.EMAIL, expireMinutes);
        log.warn("Communication mock mode is active; verification emails are logged, not sent");
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        log.info("[MOCK][EMAIL] target={}, scene={}, code={}, ttl={}min",
                MaskUtil.maskEmail(target), scene, code, expireMinutes);
    }
}
