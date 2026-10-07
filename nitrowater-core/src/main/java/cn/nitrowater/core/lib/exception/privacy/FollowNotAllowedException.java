package cn.nitrowater.core.lib.exception.privacy;

import cn.nitrowater.core.lib.api.BaseResponseCode;

/**
 * Thrown when a user tries to follow another user but the target's privacy settings
 * (allowFollow = false) prevent it.
 */
public class FollowNotAllowedException extends UserPrivacyBlockException {
    public FollowNotAllowedException() {
        super(BaseResponseCode.PRIVACY_FOLLOW_NOT_ALLOWED);
    }
}
