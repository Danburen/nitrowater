package cn.nitrowater.core.lib.exception.threshold;

import org.springframework.http.HttpStatus;
import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class RateLimitException extends BizException {
    public RateLimitException() {
        super(BaseResponseCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);
    }
}
