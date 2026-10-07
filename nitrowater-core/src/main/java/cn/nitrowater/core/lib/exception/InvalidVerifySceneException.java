package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class InvalidVerifySceneException extends BizException{
    public InvalidVerifySceneException() {
        super(BaseResponseCode.INVALID_VERIFY_SCENE);
    }
}
