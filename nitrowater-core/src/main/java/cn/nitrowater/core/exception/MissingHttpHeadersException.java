package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class MissingHttpHeadersException extends BizException{
    public MissingHttpHeadersException(String... headers) {
        super(BaseResponseCode.REQUEST_HEADER_MISSING, String.join(",", headers));
    }
}
