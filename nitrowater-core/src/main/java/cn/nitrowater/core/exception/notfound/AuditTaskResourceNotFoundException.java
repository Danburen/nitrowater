package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.lib.api.BaseResponseCode;

public class AuditTaskResourceNotFoundException extends NotFoundException {
    public AuditTaskResourceNotFoundException() {
        super(BaseResponseCode.AUDIT_TASK_RESOURCE_NOT_FOUND);
    }
}
