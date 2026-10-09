package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class InvalidVerifySceneException extends BizException{
    public InvalidVerifySceneException() {
        super(BaseResponseCode.INVALID_VERIFY_SCENE);
    }
}
