package cn.nitrowater.core.lib.services.auth.code.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import cn.nitrowater.core.lib.api.auth.VerifyChannel;
import cn.nitrowater.core.lib.api.auth.VerifyScene;
import cn.nitrowater.core.lib.api.resp.auth.CodeResult;
import cn.nitrowater.core.lib.common.RedisKeyPrefix;
import cn.nitrowater.core.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.core.lib.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.lib.services.auth.code.CodeSender;
import cn.nitrowater.core.lib.services.auth.code.CodeVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 临时（开发期）短信验证码发送器：不接云通道，仅把验证码打印到控制台并写入 Redis。
 * <p>用于 Phase 1 打通登录/注册链路；后续以 AliyunSmsService 真实实现替换（决策点 N4）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleSmsCodeSender implements CodeSender, CodeVerifier {

    private final RedisHelperHolder redisHelper;

    @Value("${expire.sms-code:2}")
    private Long expireMinutes;

    private static String codeKey(String target, String scene, String identifier) {
        return RedisKeyBuilder.build(RedisKeyPrefix.VERIFY, "sms", target, scene, identifier);
    }

    @Override
    public CodeResult sendCode(String target, VerifyScene scene) {
        String code = (String) generateVerifyCode();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        log.info("[ConsoleCodeSender][SMS] >>> target={}, scene={}, code={} (TTL {} min)",
                target, scene, code, expireMinutes);
        redisHelper.set(codeKey(target, scene.getValue(), uuid), code, Duration.ofMinutes(expireMinutes));
        return CodeResult.success(target, VerifyChannel.SMS).withKey(uuid);
    }

    @Override
    public boolean verifyCode(String target, VerifyScene scene, String key, String code) {
        return redisHelper.validateAndRemove(codeKey(target, scene.getValue(), key), code);
    }

    @Override
    public Object generateVerifyCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    @Override
    public VerifyChannel channel() {
        return VerifyChannel.SMS;
    }
}
