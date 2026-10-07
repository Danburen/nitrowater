package cn.nitrowater.core.exception.io;

import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class FileTypeNotAllowException extends BizException {
    public FileTypeNotAllowException() {
        super(BaseResponseCode.FILE_TYPE_NOT_ALLOW);
    }

    public FileTypeNotAllowException(Object... args){
        super(BaseResponseCode.FILE_TYPE_NOT_ALLOW_ARGS, args);
    }
}
