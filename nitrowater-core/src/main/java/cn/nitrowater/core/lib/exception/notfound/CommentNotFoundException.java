package cn.nitrowater.core.lib.exception.notfound;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class CommentNotFoundException extends NotFoundException {
    public CommentNotFoundException() {
        super(BaseResponseCode.COMMENT_NOT_FOUND);
    }
}
