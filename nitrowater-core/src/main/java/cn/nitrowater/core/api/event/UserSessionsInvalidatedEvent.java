package cn.nitrowater.core.api.event;

/**
 * Published when something that is already baked into issued tokens has changed for an account
 * (its password, or its role bindings), so every session minted before the change must die.
 *
 * <p>Access tokens carry a snapshot of the identity and the {@code roles} claim, and the
 * Authorization Server keeps that snapshot inside the stored {@code OAuth2Authorization} — a
 * refresh therefore re-mints the OLD claims. Without this event a password reset leaves a stolen
 * session alive (access token ≤ 15 minutes, refresh token up to 30 days) and revoking
 * {@code ROLE_ADMIN} leaves the account an admin for just as long.</p>
 *
 * <p>Consumers are expected to run synchronously, inside the publisher's transaction, so the
 * revocation commits (or rolls back) together with the change itself.</p>
 *
 * @param uid    account whose sessions must be invalidated
 * @param reason what changed; for logging and for consumers that only care about some changes
 */
public record UserSessionsInvalidatedEvent(long uid, Reason reason) {

    /** Why the sessions are being invalidated. */
    public enum Reason {
        /** Password changed or reset through a re-auth token. */
        CREDENTIALS_CHANGED,
        /** Role bindings replaced, which changes the token's {@code roles} claim. */
        ROLES_CHANGED
    }
}
