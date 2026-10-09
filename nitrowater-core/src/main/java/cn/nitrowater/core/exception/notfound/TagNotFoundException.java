package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.lib.api.BaseResponseCode;

public class TagNotFoundException extends NotFoundException {
    public TagNotFoundException() {
        super(BaseResponseCode.POST_TAG_NOT_FOUND);
    }

}
