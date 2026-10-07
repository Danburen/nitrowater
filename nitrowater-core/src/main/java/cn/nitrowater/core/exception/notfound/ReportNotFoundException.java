package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.core.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class ReportNotFoundException extends BizException {
    public ReportNotFoundException() {
        super(BaseResponseCode.REPORT_NOT_FOUND);
    }
}
