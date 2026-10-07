package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.core.api.BaseResponseCode;

public class BannerNotFoundException extends NotFoundException {
    public BannerNotFoundException() {
        super(BaseResponseCode.BANNER_NOT_FOUND);
    }
}
