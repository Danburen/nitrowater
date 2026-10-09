package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class InappropriateContentException extends BizException {

    public InappropriateContentException() {
        super(BaseResponseCode.CONTENT_INAPPROPRIATE);
    }

}
