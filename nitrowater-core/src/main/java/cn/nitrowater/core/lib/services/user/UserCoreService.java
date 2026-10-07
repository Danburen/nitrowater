package cn.nitrowater.core.lib.services.user;

import cn.nitrowater.core.lib.entity.user.User;
import cn.nitrowater.core.lib.exception.notfound.NotFoundException;

/**
 * Core user credential service (SSO scope).
 * <p>Copied and trimmed from waterfun: only password/user/identifier methods retained.</p>
 */
public interface UserCoreService {

    /**
     * Change password (rejects if new password equals current one).
     *
     * @param userUid target user uid
     * @param newPwd  raw new password
     * @return saved user
     */
    User changePwd(long userUid, String newPwd);

    /**
     * Get a user by user uid.
     *
     * @param userUid target userUid
     * @return user entity
     * @throws NotFoundException if user not found
     */
    User getUser(long userUid);

    /**
     * Resolve a login identifier (phone/email/username) to user UID.
     * Search order: phone hash → email hash → username.
     *
     * @param identifier phone number, email address, or username
     * @return user UID
     * @throws NotFoundException if no user matches the identifier
     */
    Long resolveUid(String identifier);
}
