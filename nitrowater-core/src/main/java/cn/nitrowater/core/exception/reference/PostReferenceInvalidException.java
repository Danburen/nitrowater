package cn.nitrowater.core.exception.reference;

import cn.nitrowater.lib.api.BaseResponseCode;

import java.io.Serializable;

public class PostReferenceInvalidException extends ReferenceInvalidException{
    public PostReferenceInvalidException(Serializable reference) {
        super(BaseResponseCode.POST_NOT_FOUND_ARGS, reference);
    }
}
