package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class UserCollectExceedLimitException extends BizException{
    public UserCollectExceedLimitException() {
        super(BaseResponseCode.USER_COLLECT_EXCEED_LIMIT);
    }
}
