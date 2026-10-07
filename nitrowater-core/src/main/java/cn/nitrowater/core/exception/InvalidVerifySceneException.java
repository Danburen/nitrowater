package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class InvalidVerifySceneException extends BizException{
    public InvalidVerifySceneException() {
        super(BaseResponseCode.INVALID_VERIFY_SCENE);
    }
}
