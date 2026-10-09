package cn.nitrowater.core.exception.conflict;

import org.springframework.http.HttpStatus;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class ConflictException extends BizException {
    public ConflictException(BaseResponseCode code, Object... args) {
        super(code, HttpStatus.CONFLICT,args);
    }
}
