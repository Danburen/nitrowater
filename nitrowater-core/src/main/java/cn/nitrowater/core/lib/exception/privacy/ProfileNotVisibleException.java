package cn.nitrowater.core.lib.exception.privacy;

import cn.nitrowater.core.lib.api.BaseResponseCode;

/**
 * Thrown when a user's profile/card is requested but the target's privacy settings
 * (profileVisibility) prevent the requesting user from viewing it.
 */
public class ProfileNotVisibleException extends UserPrivacyBlockException {
    public ProfileNotVisibleException() {
        super(BaseResponseCode.PRIVACY_PROFILE_NOT_VISIBLE);
    }
}
