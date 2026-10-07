package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException() {
        super(BaseResponseCode.POST_CATEGORY_NOT_FOUND);
    }
}
