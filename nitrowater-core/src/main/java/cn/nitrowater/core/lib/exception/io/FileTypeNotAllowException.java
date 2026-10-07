package cn.nitrowater.core.lib.exception.io;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class FileTypeNotAllowException extends BizException {
    public FileTypeNotAllowException() {
        super(BaseResponseCode.FILE_TYPE_NOT_ALLOW);
    }

    public FileTypeNotAllowException(Object... args){
        super(BaseResponseCode.FILE_TYPE_NOT_ALLOW_ARGS, args);
    }
}
