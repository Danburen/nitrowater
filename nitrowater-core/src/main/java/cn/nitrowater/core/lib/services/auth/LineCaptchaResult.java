package cn.nitrowater.core.lib.services.auth;

import cn.hutool.captcha.LineCaptcha;

public record LineCaptchaResult(String uuid, LineCaptcha captcha) {
}
