package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class TokenInvalidOrExpireException extends BizException{
    public TokenInvalidOrExpireException() {
        super(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
    }
}
