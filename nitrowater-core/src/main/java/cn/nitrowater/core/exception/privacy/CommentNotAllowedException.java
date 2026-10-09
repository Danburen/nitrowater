package cn.nitrowater.core.exception.privacy;

import cn.nitrowater.lib.api.BaseResponseCode;

/**
 * Thrown when a user tries to comment on a post but the post author's privacy settings
 * (commentPermission) prevent it.
 */
public class CommentNotAllowedException extends UserPrivacyBlockException {
    public CommentNotAllowedException() {
        super(BaseResponseCode.PRIVACY_COMMENT_NOT_ALLOWED);
    }
}
