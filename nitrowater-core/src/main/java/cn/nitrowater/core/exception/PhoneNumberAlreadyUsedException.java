package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class PhoneNumberAlreadyUsedException extends BizException {
    public PhoneNumberAlreadyUsedException() {
        super(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
    }
}
