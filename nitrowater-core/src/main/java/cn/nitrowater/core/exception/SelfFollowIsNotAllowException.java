package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class SelfFollowIsNotAllowException extends BizException{
    public SelfFollowIsNotAllowException() {
        super(BaseResponseCode.USER_FOLLOW_SELF_NOT_ALLOW);
    }
}
