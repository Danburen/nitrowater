package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class MissingHttpHeadersException extends BizException{
    public MissingHttpHeadersException(String... headers) {
        super(BaseResponseCode.REQUEST_HEADER_MISSING, String.join(",", headers));
    }
}
