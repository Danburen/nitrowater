package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class SelfFollowIsNotAllowException extends BizException{
    public SelfFollowIsNotAllowException() {
        super(BaseResponseCode.USER_FOLLOW_SELF_NOT_ALLOW);
    }
}
