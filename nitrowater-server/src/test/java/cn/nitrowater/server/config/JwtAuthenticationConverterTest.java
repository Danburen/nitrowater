package cn.nitrowater.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the claim → authority mapping of the Resource Server: {@code roles} must become
 * {@code ROLE_*} authorities (this is what {@code .hasRole("...")} matches against) while
 * {@code scope} keeps its default {@code SCOPE_*} form.
 */
class JwtAuthenticationConverterTest {

    private static final String ISSUER = "http://localhost:8090";
    private static final String JWKS = "http://localhost:8090/oauth2/jwks";

    private final SecurityConfig config = new SecurityConfig(ISSUER, JWKS, List.of("nitrowater-web"));

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("1083780846")
                .claim("uid", "1083780846");
    }

    /**
     * Authorities this project's converter is responsible for.
     *
     * <p>Spring Security 7 appends {@link FactorGrantedAuthority} — {@code FACTOR_BEARER} for any
     * bearer-token authentication — on top of whatever the converter returns. It backs the new
     * multi-factor authorization support and is not part of this mapping, so it is filtered out
     * to keep these assertions about the claim mapping only.
     */
    private static Set<String> authoritiesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !FactorGrantedAuthority.BEARER_AUTHORITY.equals(authority))
                .collect(Collectors.toSet());
    }

    @Test
    void springAddsTheBearerFactorAuthorityOnTopOfTheMappedAuthorities() {
        Jwt jwt = jwt().claim("roles", List.of("ROLE_USER")).build();

        Set<String> all = config.jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(all.contains(FactorGrantedAuthority.BEARER_AUTHORITY),
                "Spring Security 7 should add FACTOR_BEARER, got: " + all);
    }

    @Test
    void mapsRolesAndScopeTogether() {
        Jwt jwt = jwt()
                .claim("roles", List.of("ROLE_USER"))
                .claim("scope", List.of("openid", "profile"))
                .build();

        Set<String> authorities = authoritiesOf(config.jwtAuthenticationConverter().convert(jwt));

        assertEquals(Set.of("ROLE_USER", "SCOPE_openid", "SCOPE_profile"), authorities);
    }

    @Test
    void supportsSpaceDelimitedScopeString() {
        Jwt jwt = jwt()
                .claim("roles", List.of("ROLE_USER"))
                .claim("scope", "openid profile")
                .build();

        Set<String> authorities = authoritiesOf(config.jwtAuthenticationConverter().convert(jwt));

        assertEquals(Set.of("ROLE_USER", "SCOPE_openid", "SCOPE_profile"), authorities);
    }

    @Test
    void keepsRolePrefixVerbatimSoHasRoleMatches() {
        Jwt jwt = jwt().claim("roles", List.of("ROLE_ADMIN", "ROLE_USER")).build();

        Collection<String> roles = authoritiesOf(config.jwtAuthenticationConverter().convert(jwt))
                .stream()
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER"), roles);
    }

    @Test
    void toleratesMissingRolesClaim() {
        Jwt jwt = jwt().claim("scope", List.of("openid")).build();

        Set<String> authorities = authoritiesOf(config.jwtAuthenticationConverter().convert(jwt));

        assertEquals(Set.of("SCOPE_openid"), authorities);
    }

    @Test
    void ignoresBlankRoleEntries() {
        Jwt jwt = jwt().claim("roles", List.of("ROLE_USER", "", "  ")).build();

        Set<String> authorities = authoritiesOf(config.jwtAuthenticationConverter().convert(jwt));

        assertEquals(Set.of("ROLE_USER"), authorities);
    }

    @Test
    void usesSubAsPrincipalBecauseTheIssuerSetsItToUid() {
        Jwt jwt = jwt().claim("roles", List.of("ROLE_USER")).build();

        assertEquals("1083780846", config.jwtAuthenticationConverter().convert(jwt).getName());
    }
}
