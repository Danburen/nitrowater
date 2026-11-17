package cn.nitrowater.account.admin.dto;

/**
 * Outcome of a "revoke every session" operation.
 *
 * @param epoch          the new shared session epoch every later login is stamped with
 * @param authorizations OAuth2 authorization rows removed (this is what kills refresh tokens)
 * @param ssoSessions    account SSO sessions deleted from Redis
 */
public record RevokedSessionsVO(String epoch, int authorizations, int ssoSessions) {
}
