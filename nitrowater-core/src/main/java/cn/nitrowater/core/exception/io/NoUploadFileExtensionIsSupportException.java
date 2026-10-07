package cn.nitrowater.core.exception.io;

import cn.nitrowater.core.api.BaseResponseCode;

public class NoUploadFileExtensionIsSupportException extends IllegalUploadArgumentException {
    public NoUploadFileExtensionIsSupportException() {
        super(BaseResponseCode.ILLEGAL_UPLOAD_FILE_EXTENSION);
    }
}
