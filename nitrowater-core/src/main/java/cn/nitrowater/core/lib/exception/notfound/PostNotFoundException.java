package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class PostNotFoundException extends NotFoundException {
    public PostNotFoundException() {
        super(BaseResponseCode.POST_NOT_FOUND);
    }
}
