package cn.nitrowater.core.application.user;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.exception.notfound.NotFoundException;

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
     * Update the display nickname.
     *
     * @param userUid target uid
     * @param nickname trimmed nickname, 1-12 characters
     * @return saved user
     */
    User updateNickname(long userUid, String nickname);

    /**
     * Get a user by user uid.
     *
     * @param userUid target userUid
     * @return user entity
     * @throws NotFoundException if user not found
     */
    User getUser(long userUid);

    /**
     * Resolve a password-login identifier to a user UID.
     *
     * <p>The identifier shape selects exactly one lookup: an email-shaped value only matches
     * {@code email_hash}, a phone-shaped value only matches {@code phone_hash}, and anything
     * else only matches {@code username}. This removes the cross-namespace ambiguity of the
     * previous "try all three" order.</p>
     *
     * @param identifier phone number, email address, or username
     * @return user UID
     * @throws NotFoundException if no user matches the identifier
     */
    Long resolveUid(String identifier);

    /**
     * Resolve a one-time-code login target to a user UID.
     *
     * <p>Only the channel's own hash is consulted — usernames are never accepted here, because
     * quick login must prove control of a phone number or mailbox.</p>
     *
     * @param target  phone number or email address
     * @param channel {@link VerifyChannel#SMS} or {@link VerifyChannel#EMAIL}
     * @return user UID
     * @throws NotFoundException if the target is not bound to any account
     */
    Long resolveUidByTarget(String target, VerifyChannel channel);
}
