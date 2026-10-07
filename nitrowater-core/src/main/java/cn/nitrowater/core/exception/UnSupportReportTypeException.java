package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class UnSupportReportTypeException extends BizException {
    public UnSupportReportTypeException() {
        super(BaseResponseCode.UNSUPPORTED_REPORT_TYPE);
    }
}
