package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class UserNameAlreadyExistException extends BizException{
    public UserNameAlreadyExistException() {
        super(BaseResponseCode.USERNAME_ALREADY_REGISTERED);
    }
}
