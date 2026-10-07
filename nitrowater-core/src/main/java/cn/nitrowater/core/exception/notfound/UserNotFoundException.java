package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.core.api.BaseResponseCode;

import java.io.Serializable;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super(BaseResponseCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(Serializable id) {
        super(BaseResponseCode.USER_NOT_FOUND_ARGS, id);
    }
}
