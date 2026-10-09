package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class UserCollectExceedLimitException extends BizException{
    public UserCollectExceedLimitException() {
        super(BaseResponseCode.USER_COLLECT_EXCEED_LIMIT);
    }
}
