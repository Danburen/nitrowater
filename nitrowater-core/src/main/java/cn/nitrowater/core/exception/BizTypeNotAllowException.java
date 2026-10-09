package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class BizTypeNotAllowException extends BizException {
    public BizTypeNotAllowException(String origin, String... allowTypes) {
        super(BaseResponseCode.BIZ_TYPE_NOT_ALLOW_ARGS, origin, allowTypes);
    }
}
