package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class BannerNotFoundException extends NotFoundException {
    public BannerNotFoundException() {
        super(BaseResponseCode.BANNER_NOT_FOUND);
    }
}
