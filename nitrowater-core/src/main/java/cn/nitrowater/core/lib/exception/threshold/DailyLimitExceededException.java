package cn.nitrowater.core.lib.exception.threshold;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class DailyLimitExceededException extends BizException {
    public DailyLimitExceededException() {
        super(BaseResponseCode.DAILY_LIMIT_EXCEEDED);
    }
}
