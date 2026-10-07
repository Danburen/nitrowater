package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.AuthCode;
import cn.nitrowater.core.common.exceptions.AuthException;

public class CaptchaInvalidException extends AuthException {
    public CaptchaInvalidException() {
        super(AuthCode.CAPTCHA_INVALID);
    }
}
