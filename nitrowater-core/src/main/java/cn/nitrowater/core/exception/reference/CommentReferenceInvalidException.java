package cn.nitrowater.core.exception.reference;

import cn.nitrowater.core.api.BaseResponseCode;

import java.io.Serializable;

public class CommentReferenceInvalidException extends ReferenceInvalidException{
    public CommentReferenceInvalidException(Serializable reference) {
        super(BaseResponseCode.COMMENT_NOT_FOUND_ARGS, reference);
    }
}
