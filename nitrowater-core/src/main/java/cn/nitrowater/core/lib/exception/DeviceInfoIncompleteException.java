package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class DeviceInfoIncompleteException extends BizException{
    public DeviceInfoIncompleteException() {
        super(BaseResponseCode.DEVICE_INFO_INCOMPLETE);
    }
}
