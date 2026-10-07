package cn.nitrowater.core.exception.conflict;

import cn.nitrowater.core.api.BaseResponseCode;

public class TagConflictException extends ConflictException{
    public TagConflictException(){
        super(BaseResponseCode.POST_TAG_CONFLICT);
    }
}
