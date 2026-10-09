package cn.nitrowater.core.exception.privacy;

import org.springframework.http.HttpStatus;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

/**
 * Base exception for all privacy-blocked scenarios.
 * Returns HTTP 403 Forbidden.
 */
public class UserPrivacyBlockException extends BizException {
    public UserPrivacyBlockException(BaseResponseCode code) {
        super(code, HttpStatus.FORBIDDEN);
    }
}
