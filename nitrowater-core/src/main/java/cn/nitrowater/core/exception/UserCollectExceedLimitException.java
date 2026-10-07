package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class UserCollectExceedLimitException extends BizException{
    public UserCollectExceedLimitException() {
        super(BaseResponseCode.USER_COLLECT_EXCEED_LIMIT);
    }
}
