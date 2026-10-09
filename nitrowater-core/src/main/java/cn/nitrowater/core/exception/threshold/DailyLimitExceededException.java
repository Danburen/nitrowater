package cn.nitrowater.core.exception.threshold;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class DailyLimitExceededException extends BizException {
    public DailyLimitExceededException() {
        super(BaseResponseCode.DAILY_LIMIT_EXCEEDED);
    }
}
