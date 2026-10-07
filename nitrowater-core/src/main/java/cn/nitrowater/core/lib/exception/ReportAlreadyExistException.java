package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class ReportAlreadyExistException extends BizException {
    public ReportAlreadyExistException() {
        super(BaseResponseCode.REPORT_ALREADY_EXISTS);
    }
}
