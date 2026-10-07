package cn.nitrowater.core.lib.exception.reference;

import cn.nitrowater.core.lib.api.BaseResponseCode;

import java.io.Serializable;

public class TagReferenceInvalidException extends ReferenceInvalidException {
    public TagReferenceInvalidException(Serializable reference) {
        super(BaseResponseCode.POST_TAG_NOT_FOUND_ARGS, reference);
    }
}
