package cn.nitrowater.core.exception.threshold;

import org.springframework.http.HttpStatus;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

/**
 * A caller is asking for something too often. The wire answer is the same generic
 * {@code TOO_MANY_REQUEST} 429 for every bucket; clients restart their local resend countdown
 * instead of parsing a remaining-seconds payload.
 */
public class RateLimitException extends BizException {

    public RateLimitException() {
        super(BaseResponseCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);
    }
}
