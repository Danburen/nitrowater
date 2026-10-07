package cn.nitrowater.core.lib.exception.io;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class IllegalUploadCountException extends IllegalUploadArgumentException{
    public IllegalUploadCountException(int count) {
        super(BaseResponseCode.ILLEGAL_FILE_COUNT, count);
    }
}
