package cn.nitrowater.core.exception.io;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class UnsupportedFileExtension extends BizException {
    public UnsupportedFileExtension(String extension, String original) {
      super(BaseResponseCode.UNSUPPORTED_FILE_EXTENSION, extension, original);
    }
}
