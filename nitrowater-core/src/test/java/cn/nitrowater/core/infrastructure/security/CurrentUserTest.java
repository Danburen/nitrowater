package cn.nitrowater.core.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the single uid rule: the explicit {@code uid} claim wins over {@code sub}, both are parsed
 * as numbers, and a {@code JwtAuthenticationToken} is unwrapped to its JWT principal. This is the
 * rule server / resource / account all share, so a change here is a change everywhere.
 */
class CurrentUserTest {

    @Test
    void prefersTheUidClaimOverTheSubject() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("999").claim("uid", "7").build();

        assertThat(CurrentUser.uidOf(jwt)).hasValue(7);
    }

    @Test
    void fallsBackToTheSubject() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("42").build();

        assertThat(CurrentUser.uidOf(jwt)).hasValue(42);
    }

    @Test
    void unwrapsTheAuthenticationPrincipal() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("42").claim("uid", "42").build();

        assertThat(CurrentUser.uidOf(new JwtAuthenticationToken(jwt))).hasValue(42);
    }

    @Test
    void readsAPlainPrincipalName() {
        Principal principal = () -> "123";

        assertThat(CurrentUser.uidOf(principal)).hasValue(123);
    }

    @Test
    void emptyForAnonymousOrNonNumericPrincipals() {
        Jwt opaque = Jwt.withTokenValue("t").header("alg", "none").subject("external|abc").build();

        assertThat(CurrentUser.uidOf((Principal) null)).isEmpty();
        assertThat(CurrentUser.uidOf(opaque)).isEmpty();
    }

    @Test
    void requireUidRejectsAMissingIdentityWith401() {
        Jwt opaque = Jwt.withTokenValue("t").header("alg", "none").subject("external|abc").build();

        assertThatThrownBy(() -> CurrentUser.requireUid(new JwtAuthenticationToken(opaque)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(() -> CurrentUser.requireUid(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }
}
