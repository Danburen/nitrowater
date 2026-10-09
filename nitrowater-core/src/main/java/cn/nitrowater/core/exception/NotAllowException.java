package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class NotAllowException extends ForbiddenException{
    public NotAllowException() {
        super(BaseResponseCode.NOT_ALLOW);
    }
}
