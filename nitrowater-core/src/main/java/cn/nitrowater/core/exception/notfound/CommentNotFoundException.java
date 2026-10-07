package cn.nitrowater.core.exception.notfound;

import cn.nitrowater.core.api.BaseResponseCode;

public class CommentNotFoundException extends NotFoundException {
    public CommentNotFoundException() {
        super(BaseResponseCode.COMMENT_NOT_FOUND);
    }
}
