package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class NotAllowException extends ForbiddenException{
    public NotAllowException() {
        super(BaseResponseCode.NOT_ALLOW);
    }
}
