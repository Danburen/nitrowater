package cn.nitrowater.core.lib.exception.conflict;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class TagConflictException extends ConflictException{
    public TagConflictException(){
        super(BaseResponseCode.POST_TAG_CONFLICT);
    }
}
