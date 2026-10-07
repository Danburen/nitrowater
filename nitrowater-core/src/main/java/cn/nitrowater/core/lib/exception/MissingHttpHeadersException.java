package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class MissingHttpHeadersException extends BizException{
    public MissingHttpHeadersException(String... headers) {
        super(BaseResponseCode.REQUEST_HEADER_MISSING, String.join(",", headers));
    }
}
