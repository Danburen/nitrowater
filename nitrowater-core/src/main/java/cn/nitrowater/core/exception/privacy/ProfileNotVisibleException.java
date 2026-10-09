package cn.nitrowater.core.exception.privacy;

import cn.nitrowater.lib.api.BaseResponseCode;

/**
 * Thrown when a user's profile/card is requested but the target's privacy settings
 * (profileVisibility) prevent the requesting user from viewing it.
 */
public class ProfileNotVisibleException extends UserPrivacyBlockException {
    public ProfileNotVisibleException() {
        super(BaseResponseCode.PRIVACY_PROFILE_NOT_VISIBLE);
    }
}
