package cn.nitrowater.core.lib.exception.io;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class UnsupportedFileExtension extends BizException {
    public UnsupportedFileExtension(String extension, String original) {
      super(BaseResponseCode.UNSUPPORTED_FILE_EXTENSION, extension, original);
    }
}
