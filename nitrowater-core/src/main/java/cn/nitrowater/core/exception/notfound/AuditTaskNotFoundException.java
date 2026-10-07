package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.core.api.BaseResponseCode;

public class AuditTaskNotFoundException extends NotFoundException {
    public AuditTaskNotFoundException() {
        super(BaseResponseCode.AUDIT_TASK_NOT_FOUND);
    }
}
