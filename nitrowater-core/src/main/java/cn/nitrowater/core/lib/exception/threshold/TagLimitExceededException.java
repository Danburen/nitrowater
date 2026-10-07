package cn.nitrowater.core.lib.exception.threshold;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class TagLimitExceededException extends BizException {
    public TagLimitExceededException() {
        super(BaseResponseCode.USER_TAG_QUOTA_EXCEEDED);
    }
}
