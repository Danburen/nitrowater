package cn.nitrowater.core.exception.reference;

import cn.nitrowater.lib.api.BaseResponseCode;

import java.io.Serializable;

public class UserReferenceInvalidException extends ReferenceInvalidException {
    public UserReferenceInvalidException(Serializable reference) {
        super(BaseResponseCode.USER_NOT_FOUND_ARGS, reference);
    }
}
