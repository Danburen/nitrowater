package cn.nitrowater.core.lib.exception;

import org.springframework.http.HttpStatus;
import cn.nitrowater.core.lib.api.BaseResponseCode;

public class ForbiddenException extends BizException {
    public ForbiddenException() {
        super(BaseResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(BaseResponseCode baseResponseCode) {
        super(baseResponseCode, HttpStatus.FORBIDDEN);
    }
}

