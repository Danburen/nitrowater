package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.common.exceptions.AuthException;

public class CaptchaInvalidException extends AuthException {
    public CaptchaInvalidException() {
        super(AuthCode.CAPTCHA_INVALID);
    }
}
