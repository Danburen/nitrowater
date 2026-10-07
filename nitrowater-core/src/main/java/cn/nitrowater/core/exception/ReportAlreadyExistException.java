package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class ReportAlreadyExistException extends BizException {
    public ReportAlreadyExistException() {
        super(BaseResponseCode.REPORT_ALREADY_EXISTS);
    }
}
