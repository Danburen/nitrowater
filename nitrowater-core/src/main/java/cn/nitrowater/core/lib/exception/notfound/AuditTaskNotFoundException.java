package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class AuditTaskNotFoundException extends NotFoundException {
    public AuditTaskNotFoundException() {
        super(BaseResponseCode.AUDIT_TASK_NOT_FOUND);
    }
}
