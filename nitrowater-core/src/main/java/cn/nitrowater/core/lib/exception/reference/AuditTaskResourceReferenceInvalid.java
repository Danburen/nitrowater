package cn.nitrowater.core.lib.exception.reference;

import cn.nitrowater.core.lib.api.BaseResponseCode;

import java.io.Serializable;

public class AuditTaskResourceReferenceInvalid extends ReferenceInvalidException{
    public AuditTaskResourceReferenceInvalid(Serializable reference) {
        super(BaseResponseCode.AUDIT_TASK_RESOURCE_NOT_FOUND, reference);
    }
}
