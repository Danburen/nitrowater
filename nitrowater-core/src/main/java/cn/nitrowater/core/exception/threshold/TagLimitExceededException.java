package cn.nitrowater.core.exception.threshold;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class TagLimitExceededException extends BizException {
    public TagLimitExceededException() {
        super(BaseResponseCode.USER_TAG_QUOTA_EXCEEDED);
    }
}
