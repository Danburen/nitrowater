package cn.nitrowater.core.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.OptionalLong;

/**
 * The single rule for reading the account uid out of an authenticated principal.
 *
 * <p>Every service used to carry its own copy: the business API parsed {@code principal.getName()}
 * (the JWT {@code sub}), the resource service preferred the explicit {@code uid} claim, and the
 * account module parsed {@code sub} again inside its filter. They agree on today's tokens — the
 * Authorization Server sets {@code sub} and {@code uid} to the same value — but would silently
 * diverge the day an external IdP puts an opaque value in {@code sub}.</p>
 *
 * <p>The rule is therefore: <b>{@code uid} claim first, then {@code sub}</b>, both numeric. A
 * {@link Jwt} is handled directly; a {@code JwtAuthenticationToken} is unwrapped to its principal;
 * anything else falls back to {@link Principal#getName()} so plain test/session principals keep
 * working.</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @param jwt decoded token, or {@code null}
     * @return the numeric {@code uid} claim, falling back to {@code sub}
     */
    public static OptionalLong uidOf(Jwt jwt) {
        if (jwt == null) {
            return OptionalLong.empty();
        }
        OptionalLong uid = parse(jwt.getClaimAsString("uid"));
        return uid.isPresent() ? uid : parse(jwt.getSubject());
    }

    /**
     * @param principal authenticated principal ({@code Authentication} or {@link Jwt}), may be {@code null}
     * @return the numeric account uid, or empty when the principal carries none
     */
    public static OptionalLong uidOf(Principal principal) {
        if (principal == null) {
            return OptionalLong.empty();
        }
        if (principal instanceof Authentication authentication) {
            Object token = authentication.getPrincipal();
            if (token instanceof Jwt jwt) {
                return uidOf(jwt);
            }
            if (token instanceof Principal nested) {
                return uidOf(nested);
            }
            return parse(authentication.getName());
        }
        return parse(principal.getName());
    }

    /**
     * @param principal authenticated principal, may be {@code null} on a misconfigured route
     * @return the numeric account uid
     * @throws ResponseStatusException 401 when unauthenticated or when no numeric uid is present
     */
    public static long requireUid(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return uidOf(principal).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported token subject"));
    }

    private static OptionalLong parse(String value) {
        if (value == null || value.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }
}
