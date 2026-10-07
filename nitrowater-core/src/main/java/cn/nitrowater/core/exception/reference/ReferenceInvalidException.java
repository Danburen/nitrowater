package cn.nitrowater.core.exception.reference;

import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

import java.io.Serializable;

public class ReferenceInvalidException extends BizException {
    public ReferenceInvalidException() {
        super(BaseResponseCode.INVALID_REFERENCE);
    }

    public ReferenceInvalidException(BaseResponseCode baseResponseCode, Serializable reference) {
        super(baseResponseCode, reference);
    }
}
