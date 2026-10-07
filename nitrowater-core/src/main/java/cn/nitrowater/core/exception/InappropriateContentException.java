package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class InappropriateContentException extends BizException {

    public InappropriateContentException() {
        super(BaseResponseCode.CONTENT_INAPPROPRIATE);
    }

}
