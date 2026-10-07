package cn.nitrowater.core.exception.threshold;

import org.springframework.http.HttpStatus;
import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class RateLimitException extends BizException {
    public RateLimitException() {
        super(BaseResponseCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);
    }
}
