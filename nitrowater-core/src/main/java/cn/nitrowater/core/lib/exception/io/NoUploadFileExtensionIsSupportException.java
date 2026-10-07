package cn.nitrowater.core.lib.exception.io;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class NoUploadFileExtensionIsSupportException extends IllegalUploadArgumentException {
    public NoUploadFileExtensionIsSupportException() {
        super(BaseResponseCode.ILLEGAL_UPLOAD_FILE_EXTENSION);
    }
}
