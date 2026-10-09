package cn.nitrowater.core.exception.io;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class FilePathInvalidException extends BizException {
    public FilePathInvalidException() {
        super(BaseResponseCode.INVALID_PATH);
    }
}
