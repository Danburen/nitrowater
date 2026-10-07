package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class TokenInvalidOrExpireException extends BizException{
    public TokenInvalidOrExpireException() {
        super(BaseResponseCode.INVALID_TOKEN_OR_EXPIRED);
    }
}
