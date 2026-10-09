package cn.nitrowater.core.exception.privacy;

import cn.nitrowater.lib.api.BaseResponseCode;

/**
 * Thrown when a user tries to send a direct message but the recipient's privacy settings
 * (messagePermission) prevent it.
 */
public class MessageNotAllowedException extends UserPrivacyBlockException {
    public MessageNotAllowedException() {
        super(BaseResponseCode.PRIVACY_MESSAGE_NOT_ALLOWED);
    }
}
