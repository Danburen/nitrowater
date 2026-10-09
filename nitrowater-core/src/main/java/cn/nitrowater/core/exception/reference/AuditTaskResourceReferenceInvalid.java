package cn.nitrowater.core.exception.reference;

import cn.nitrowater.lib.api.BaseResponseCode;

import java.io.Serializable;

public class AuditTaskResourceReferenceInvalid extends ReferenceInvalidException{
    public AuditTaskResourceReferenceInvalid(Serializable reference) {
        super(BaseResponseCode.AUDIT_TASK_RESOURCE_NOT_FOUND, reference);
    }
}
