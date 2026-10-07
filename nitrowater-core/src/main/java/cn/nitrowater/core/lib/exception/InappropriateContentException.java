package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class InappropriateContentException extends BizException {

    public InappropriateContentException() {
        super(BaseResponseCode.CONTENT_INAPPROPRIATE);
    }

}
