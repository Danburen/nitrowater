package cn.nitrowater.core.exception.io;

import cn.nitrowater.core.api.BaseResponseCode;

public class IllegalUploadCountException extends IllegalUploadArgumentException{
    public IllegalUploadCountException(int count) {
        super(BaseResponseCode.ILLEGAL_FILE_COUNT, count);
    }
}
