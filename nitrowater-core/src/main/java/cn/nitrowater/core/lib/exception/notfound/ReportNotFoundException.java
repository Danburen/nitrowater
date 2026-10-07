package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;
import cn.nitrowater.core.lib.exception.BizException;

public class ReportNotFoundException extends BizException {
    public ReportNotFoundException() {
        super(BaseResponseCode.REPORT_NOT_FOUND);
    }
}
