package cn.nitrowater.core.lib.exception.reference;

import cn.nitrowater.core.lib.api.BaseResponseCode;

import java.io.Serializable;

public class PostReferenceInvalidException extends ReferenceInvalidException{
    public PostReferenceInvalidException(Serializable reference) {
        super(BaseResponseCode.POST_NOT_FOUND_ARGS, reference);
    }
}
