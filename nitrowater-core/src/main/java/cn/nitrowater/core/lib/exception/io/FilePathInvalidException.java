package cn.nitrowater.core.lib.exception.io;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class FilePathInvalidException extends BizException {
    public FilePathInvalidException() {
        super(BaseResponseCode.INVALID_PATH);
    }
}
