package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class CommentAlreadyDeletedOrNotFoundException extends BizException{
    public CommentAlreadyDeletedOrNotFoundException() {
        super(BaseResponseCode.COMMENT_ALREADY_DELETED_OR_NOT_FOUND);
    }
}
