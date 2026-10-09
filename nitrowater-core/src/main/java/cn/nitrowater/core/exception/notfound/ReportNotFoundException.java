package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.core.exception.BizException;

public class ReportNotFoundException extends BizException {
    public ReportNotFoundException() {
        super(BaseResponseCode.REPORT_NOT_FOUND);
    }
}
