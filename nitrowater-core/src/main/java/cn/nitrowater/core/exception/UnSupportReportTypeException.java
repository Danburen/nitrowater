package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class UnSupportReportTypeException extends BizException {
    public UnSupportReportTypeException() {
        super(BaseResponseCode.UNSUPPORTED_REPORT_TYPE);
    }
}
