package cn.nitrowater.core.application.auth;

import cn.hutool.captcha.LineCaptcha;

public interface CaptchaService {
    LineCaptchaResult generateCaptcha();

    LineCaptcha generateLineCaptcha();

    boolean verifyCode(String uuid, String code);
}
