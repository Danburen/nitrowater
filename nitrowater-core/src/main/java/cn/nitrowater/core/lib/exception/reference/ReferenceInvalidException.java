package cn.nitrowater.core.lib.exception.reference;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

import java.io.Serializable;

public class ReferenceInvalidException extends BizException {
    public ReferenceInvalidException() {
        super(BaseResponseCode.INVALID_REFERENCE);
    }

    public ReferenceInvalidException(BaseResponseCode baseResponseCode, Serializable reference) {
        super(baseResponseCode, reference);
    }
}
