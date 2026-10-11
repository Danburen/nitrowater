package cn.nitrowater.core.application.auth;

import cn.hutool.captcha.LineCaptcha;

public record LineCaptchaResult(String uuid, LineCaptcha captcha) {
}
