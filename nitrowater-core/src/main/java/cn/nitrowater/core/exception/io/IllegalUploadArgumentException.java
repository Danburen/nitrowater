package cn.nitrowater.core.exception.io;

import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class IllegalUploadArgumentException extends BizException {

    public IllegalUploadArgumentException() {
        super(BaseResponseCode.ILLEGAL_UPLOAD_FILE_ARGUMENTS);
    }

    public IllegalUploadArgumentException(BaseResponseCode code) {
        super(code);
    }

    public IllegalUploadArgumentException(BaseResponseCode code, Object... args) {
        super(code, args);
    }
}
