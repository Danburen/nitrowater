package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class AuditTaskResourceNotFoundException extends NotFoundException {
    public AuditTaskResourceNotFoundException() {
        super(BaseResponseCode.AUDIT_TASK_RESOURCE_NOT_FOUND);
    }
}
