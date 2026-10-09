package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class ReportAlreadyExistException extends BizException {
    public ReportAlreadyExistException() {
        super(BaseResponseCode.REPORT_ALREADY_EXISTS);
    }
}
