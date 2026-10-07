package cn.nitrowater.core.lib.services.auth;

import cn.hutool.captcha.LineCaptcha;

public interface CaptchaService {
    LineCaptchaResult generateCaptcha();

    LineCaptcha generateLineCaptcha();

    boolean verifyCode(String uuid, String code);
}
