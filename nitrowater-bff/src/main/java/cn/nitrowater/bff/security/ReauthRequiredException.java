package cn.nitrowater.bff.security;

/**
 * Raised when the current BFF session cannot produce an access token any more: no signed-in
 * principal, no authorized client in the session, or a refresh the Authorization Server rejected.
 *
 * <p>It is deliberately <b>not</b> an {@code OAuth2AuthorizationException}: the session has already
 * been dropped by the time this is thrown, and the caller only has to render a 401 — carrying the
 * reason so the SPA can tell "session expired" from "never signed in".</p>
 */
public class ReauthRequiredException extends RuntimeException {

    private final String reason;

    /**
     * @param reason short machine-readable cause, sent back as {@code X-Auth-Reason}
     */
    public ReauthRequiredException(String reason) {
        super("re-authentication required (" + reason + ")");
        this.reason = reason;
    }

    /** @return the machine-readable cause (e.g. {@code refresh_rejected}) */
    public String reason() {
        return this.reason;
    }
}
