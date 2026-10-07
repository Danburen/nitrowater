package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class BizTypeNotAllowException extends BizException {
    public BizTypeNotAllowException(String origin, String... allowTypes) {
        super(BaseResponseCode.BIZ_TYPE_NOT_ALLOW_ARGS, origin, allowTypes);
    }
}
