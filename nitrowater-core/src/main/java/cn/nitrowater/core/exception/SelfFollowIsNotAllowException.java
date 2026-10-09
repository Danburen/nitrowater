package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class SelfFollowIsNotAllowException extends BizException{
    public SelfFollowIsNotAllowException() {
        super(BaseResponseCode.USER_FOLLOW_SELF_NOT_ALLOW);
    }
}
