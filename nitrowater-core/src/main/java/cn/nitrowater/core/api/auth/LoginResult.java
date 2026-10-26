package cn.nitrowater.core.api.auth;

import cn.nitrowater.core.domain.user.User;

/**
 * Outcome of a successful credential check.
 *
 * @param user the authenticated account
 */
public record LoginResult(User user) {
}