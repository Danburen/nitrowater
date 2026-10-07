package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.AuthCode;
import cn.nitrowater.core.lib.common.exceptions.AuthException;

public class CaptchaInvalidException extends AuthException {
    public CaptchaInvalidException() {
        super(AuthCode.CAPTCHA_INVALID);
    }
}
