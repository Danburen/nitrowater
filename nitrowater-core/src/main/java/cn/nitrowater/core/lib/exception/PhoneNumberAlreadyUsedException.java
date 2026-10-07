package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class PhoneNumberAlreadyUsedException extends BizException {
    public PhoneNumberAlreadyUsedException() {
        super(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
    }
}
