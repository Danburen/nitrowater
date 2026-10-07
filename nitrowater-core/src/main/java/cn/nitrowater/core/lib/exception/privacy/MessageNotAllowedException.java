package cn.nitrowater.core.lib.exception.privacy;

import cn.nitrowater.core.lib.api.BaseResponseCode;

/**
 * Thrown when a user tries to send a direct message but the recipient's privacy settings
 * (messagePermission) prevent it.
 */
public class MessageNotAllowedException extends UserPrivacyBlockException {
    public MessageNotAllowedException() {
        super(BaseResponseCode.PRIVACY_MESSAGE_NOT_ALLOWED);
    }
}
