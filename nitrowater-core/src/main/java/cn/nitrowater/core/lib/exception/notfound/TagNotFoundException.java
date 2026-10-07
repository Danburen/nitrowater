package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class TagNotFoundException extends NotFoundException {
    public TagNotFoundException() {
        super(BaseResponseCode.POST_TAG_NOT_FOUND);
    }

}
