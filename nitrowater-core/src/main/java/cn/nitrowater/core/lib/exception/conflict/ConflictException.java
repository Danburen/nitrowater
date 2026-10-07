package cn.nitrowater.core.lib.exception.conflict;

import org.springframework.http.HttpStatus;
import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class ConflictException extends BizException {
    public ConflictException(BaseResponseCode code, Object... args) {
        super(code, HttpStatus.CONFLICT,args);
    }
}
