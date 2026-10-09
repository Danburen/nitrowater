package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class UserNameAlreadyExistException extends BizException{
    public UserNameAlreadyExistException() {
        super(BaseResponseCode.USERNAME_ALREADY_REGISTERED);
    }
}
