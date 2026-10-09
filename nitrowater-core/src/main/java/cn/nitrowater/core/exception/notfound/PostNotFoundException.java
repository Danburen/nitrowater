package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.lib.api.BaseResponseCode;

public class PostNotFoundException extends NotFoundException {
    public PostNotFoundException() {
        super(BaseResponseCode.POST_NOT_FOUND);
    }
}
