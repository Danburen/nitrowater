package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

import java.io.Serializable;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super(BaseResponseCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(Serializable id) {
        super(BaseResponseCode.USER_NOT_FOUND_ARGS, id);
    }
}
