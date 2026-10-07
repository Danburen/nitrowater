package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class PhoneNumberAlreadyUsedException extends BizException {
    public PhoneNumberAlreadyUsedException() {
        super(BaseResponseCode.PHONE_NUMBER_ALREADY_USED);
    }
}
