package cn.nitrowater.core.lib.exception.threshold;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class AttemptLimitExceededException extends BizException {
    public AttemptLimitExceededException(long limitMinutes) {
        super(BaseResponseCode.AttemptLimitExceededException, limitMinutes);
    }
}
