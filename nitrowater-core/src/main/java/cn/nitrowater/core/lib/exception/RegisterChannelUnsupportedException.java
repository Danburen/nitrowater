package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class RegisterChannelUnsupportedException extends BizException {
    public RegisterChannelUnsupportedException() {
        super(BaseResponseCode.REGISTER_CHANNEL_UNSUPPORTED);
    }
}
