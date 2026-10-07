package cn.nitrowater.core.exception.threshold;

import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class AttemptLimitExceededException extends BizException {
    public AttemptLimitExceededException(long limitMinutes) {
        super(BaseResponseCode.AttemptLimitExceededException, limitMinutes);
    }
}
