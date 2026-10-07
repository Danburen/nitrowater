package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class UnSupportReportTypeException extends BizException {
    public UnSupportReportTypeException() {
        super(BaseResponseCode.UNSUPPORTED_REPORT_TYPE);
    }
}
