package cn.nitrowater.core.lib.exception.privacy;

import org.springframework.http.HttpStatus;
import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

/**
 * Base exception for all privacy-blocked scenarios.
 * Returns HTTP 403 Forbidden.
 */
public class UserPrivacyBlockException extends BizException {
    public UserPrivacyBlockException(BaseResponseCode code) {
        super(code, HttpStatus.FORBIDDEN);
    }
}
