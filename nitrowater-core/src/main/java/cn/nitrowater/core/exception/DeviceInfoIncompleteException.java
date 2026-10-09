package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class DeviceInfoIncompleteException extends BizException{
    public DeviceInfoIncompleteException() {
        super(BaseResponseCode.DEVICE_INFO_INCOMPLETE);
    }
}
