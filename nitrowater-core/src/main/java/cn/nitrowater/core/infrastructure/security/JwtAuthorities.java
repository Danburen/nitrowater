package cn.nitrowater.core.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * The single authority mapping for every service that validates an SSO access token.
 *
 * <p>Spring's default converter only maps {@code scope}/{@code scp}; this project's authorization
 * model lives in the {@code roles} claim (already {@code ROLE_}-prefixed by the Authorization
 * Server). Keeping one implementation means a change to that model can never apply to only one
 * service — {@code /api/admin/**} would otherwise be admin-gated in one module and not in another.</p>
 *
 * <p>{@code scope}/{@code scp} is still mapped to {@code SCOPE_*} so protocol scopes behave exactly
 * as they do under Spring's default converter, and it accepts both wire shapes (space-delimited
 * string and JSON array) because the Authorization Server emits an array.</p>
 */
public final class JwtAuthorities {

    private JwtAuthorities() {
    }

    /**
     * @return a converter that maps {@code roles} + {@code scope} to granted authorities
     */
    public static JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtAuthorities::grantedAuthorities);
        return converter;
    }

    private static Collection<GrantedAuthority> grantedAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String scope : scopesOf(jwt)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                if (StringUtil.isNotBlank(role)) {
                    authorities.add(new SimpleGrantedAuthority(role));
                }
            }
        }
        return new ArrayList<>(authorities);
    }

    /**
     * Reads {@code scope} (or its {@code scp} shorthand) whether the Authorization Server emitted it
     * as a space-delimited string or as a JSON array — Spring's own converter only reliably handles
     * one of the two shapes, and this project's tokens carry an array.
     *
     * @param jwt token being mapped
     * @return the declared scopes, never {@code null}
     */
    private static Collection<String> scopesOf(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope == null) {
            scope = jwt.getClaim("scp");
        }
        if (scope instanceof Collection<?> values) {
            List<String> result = new ArrayList<>(values.size());
            for (Object value : values) {
                if (value != null) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        if (scope instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
        }
        return List.of();
    }
}
