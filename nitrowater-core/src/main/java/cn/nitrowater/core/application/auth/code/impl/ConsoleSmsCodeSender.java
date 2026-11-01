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
 * Local-development SMS sender. Logs the code instead of delivering it.
 *
 * <p>Selected when {@code app.communication.mode=mock} (the default). Never use this mode in
 * production: verification codes appear in application logs.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode",
        havingValue = "mock", matchIfMissing = true)
public class ConsoleSmsCodeSender extends AbstractRedisCodeSender {

    public ConsoleSmsCodeSender(RedisHelperHolder redisHelper,
            @Value("${expire.sms-code:2}") long expireMinutes) {
        super(redisHelper, VerifyChannel.SMS, expireMinutes);
        log.warn("Communication mock mode is active; verification SMS are logged, not sent");
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        log.info("[MOCK][SMS] target={}, scene={}, code={}, ttl={}min",
                MaskUtil.maskPhone(target), scene, code, expireMinutes);
    }
}
