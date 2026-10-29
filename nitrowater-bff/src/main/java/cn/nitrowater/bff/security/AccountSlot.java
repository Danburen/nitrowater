package cn.nitrowater.bff.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of one signed-in account.
 *
 * <p>Only the identity is stored; the access/refresh tokens live in
 * {@link MultiAccountAuthorizedClientRepository}. The ID token is kept because rebuilding the
 * Spring Security {@code Authentication} for an inactive account needs a self-contained principal
 * — it is already sent to the browser at login and is not a bearer credential for our APIs.</p>
 *
 * <p>Instances are persisted inside the Redis-backed HTTP session, so every component must be
 * JDK-serializable. JWT claims are JSON scalars/lists/maps, all of which qualify.</p>
 *
 * @param uid             OIDC subject; also {@code OidcUser#getName()} and the repository key
 * @param username        {@code preferred_username} claim, for display
 * @param name            {@code name} claim, for display
 * @param roles           {@code roles} claim, for display and the role badge
 * @param authorities     granted authorities, replayed when the account is re-activated
 * @param idTokenValue    raw ID token value
 * @param idTokenIssuedAt ID token {@code iat}
 * @param idTokenExpiresAt ID token {@code exp}
 * @param claims          full claim set used to rebuild the principal
 * @param issuer          {@code iss} claim
 * @param did             device id claim, forwarded by the BFF when present
 * @param picture         stable avatar URL claim
 * @param loginAt         epoch millis of the most recent login, used for pool eviction order
 */
public record AccountSlot(
        String uid,
        String username,
        String name,
        List<String> roles,
        List<String> authorities,
        String idTokenValue,
        Instant idTokenIssuedAt,
        Instant idTokenExpiresAt,
        Map<String, Object> claims,
        String issuer,
        String did,
        String picture,
        long loginAt) implements Serializable {

    /** Defensive copy: callers must not mutate the persisted snapshot. */
    public AccountSlot {
        roles = roles == null ? List.of() : List.copyOf(roles);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
        claims = claims == null ? Map.of() : new LinkedHashMap<>(claims);
    }

    /**
     * Captures the signed-in principal.
     *
     * @param user authenticated OIDC principal
     * @return a snapshot suitable for the account pool
     */
    public static AccountSlot from(OidcUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        Map<String, Object> claims = new LinkedHashMap<>(user.getClaims());
        OidcIdToken idToken = user.getIdToken();
        Instant issuedAt = idToken != null ? idToken.getIssuedAt() : instantClaim(claims, "iat");
        Instant expiresAt = idToken != null ? idToken.getExpiresAt() : instantClaim(claims, "exp");
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        if (expiresAt == null) {
            expiresAt = issuedAt.plusSeconds(3600);
        }
        return new AccountSlot(
                user.getName(),
                string(claims.get("preferred_username")),
                string(claims.get("name")),
                strings(claims.get("roles")),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                idToken != null ? idToken.getTokenValue() : null,
                issuedAt,
                expiresAt,
                claims,
                string(claims.get("iss")),
                string(claims.get("did")),
                string(claims.get("picture")),
                System.currentTimeMillis());
    }

    /**
     * Rebuilds a Spring Security authentication for this account.
     *
     * <p>{@link UsernamePasswordAuthenticationToken} is used deliberately: it is JDK-serializable
     * (the OIDC token type is not part of the public serialization contract) and its
     * {@code getName()} delegates to the {@link OidcUser} principal, which returns the subject.</p>
     *
     * @return an authenticated token carrying the replayed {@link OidcUser}
     */
    public Authentication toAuthentication() {
        OidcIdToken idToken = new OidcIdToken(idTokenValue, idTokenIssuedAt, idTokenExpiresAt, claims);
        List<GrantedAuthority> granted = new ArrayList<>(authorities.size());
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        OidcUser user = new DefaultOidcUser(granted, idToken);
        return UsernamePasswordAuthenticationToken.authenticated(user, idTokenValue, granted);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static Instant instantClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
