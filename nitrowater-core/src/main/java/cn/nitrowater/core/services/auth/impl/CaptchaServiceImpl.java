package cn.nitrowater.core.services.auth.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.services.auth.CaptchaService;
import cn.nitrowater.core.services.auth.LineCaptchaResult;

import java.time.Duration;

import static cn.nitrowater.lib.common.RedisKeyPrefix.VERIFY;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {
    private final RedisHelperHolder redisHelper;

    // -- Redis key builders --
    private static String captchaKey(String uuid) {
        return RedisKeyBuilder.build(VERIFY, "captcha", uuid);
    }

    @Override
    public LineCaptchaResult generateCaptcha(){
        LineCaptcha lineCaptcha = generateLineCaptcha();
        String uuid = StringUtil.noDashRandomUUIDString();
        String code = lineCaptcha.getCode();
        redisHelper.set(captchaKey(uuid), code, Duration.ofMinutes(2));
        return new LineCaptchaResult(uuid,lineCaptcha);
    }

    @Override
    public LineCaptcha generateLineCaptcha() {
        return CaptchaUtil.createLineCaptcha(120, 30, 4, 10);
    }

    @Override
    public boolean verifyCode(String uuid, String code){
        return redisHelper.validateAndRemove(captchaKey(uuid), code);
    }
}
