package cn.nitrowater.bff.web;

import org.springframework.http.ResponseCookie;

/**
 * The BFF session cookie and a helper for expiring it.
 *
 * <p>Expiring the cookie together with invalidating the server session is what prevents a browser
 * from keeping a dead session id forever: without it, the stale cookie is sent on every request and
 * the SPA cannot tell "never signed in" from "session was lost".</p>
 */
public final class SessionCookies {

    /** Must match {@code server.servlet.session.cookie.name}. */
    public static final String NAME = "BFFSESSION";

    private SessionCookies() {
    }

    /** @return a Set-Cookie value that deletes the session cookie in the browser */
    public static ResponseCookie expired() {
        return ResponseCookie.from(NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .build();
    }
}
