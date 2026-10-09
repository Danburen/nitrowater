package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class RegisterChannelUnsupportedException extends BizException {
    public RegisterChannelUnsupportedException() {
        super(BaseResponseCode.REGISTER_CHANNEL_UNSUPPORTED);
    }
}
