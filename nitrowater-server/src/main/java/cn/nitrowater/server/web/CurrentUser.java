package cn.nitrowater.server.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/** Resolves the authenticated account uid from the security context. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The JWT subject is {@code String.valueOf(account.user.uid)}; anything else means the token was
     * not issued by this platform, so it is rejected instead of silently defaulting to a user.
     *
     * @param principal authenticated principal, may be {@code null} on a misconfigured route
     * @return the numeric account uid
     * @throws ResponseStatusException 401 when unauthenticated or when the subject is not numeric
     */
    public static long requireUid(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported token subject");
        }
    }
}
