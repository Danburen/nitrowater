package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.lib.api.BaseResponseCode;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException() {
        super(BaseResponseCode.POST_CATEGORY_NOT_FOUND);
    }
}
