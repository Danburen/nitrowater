package cn.nitrowater.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceValidatorTest {

    private static final List<String> ACCEPTED = List.of("nitrowater-web");

    private static Jwt tokenWithAudience(String... audiences) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .audience(List.of(audiences))
                .build();
    }

    @Test
    void acceptsExpectedAudience() {
        OAuth2TokenValidatorResult result = new AudienceValidator(ACCEPTED)
                .validate(tokenWithAudience("nitrowater-web"));

        assertTrue(result.getErrors().isEmpty(), "expected success, got: " + result.getErrors());
    }

    @Test
    void acceptsWhenAnyOfSeveralAcceptedAudiencesMatches() {
        OAuth2TokenValidatorResult result =
                new AudienceValidator(List.of("nitrowater-web", "nitrowater-api"))
                        .validate(tokenWithAudience("nitrowater-api"));

        assertTrue(result.getErrors().isEmpty(), "expected success, got: " + result.getErrors());
    }

    @Test
    void rejectsAudienceIssuedToAnotherClient() {
        OAuth2TokenValidatorResult result = new AudienceValidator(ACCEPTED)
                .validate(tokenWithAudience("some-other-client"));

        assertEquals(1, result.getErrors().size());
        assertEquals(
                OAuth2ErrorCodes.INVALID_TOKEN,
                result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    void rejectsTokenWithoutAudience() {
        // Jwt requires a non-empty claim map, so add an unrelated claim and leave `aud` absent
        Jwt noAudience = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1083780846")
                .build();

        OAuth2TokenValidatorResult result = new AudienceValidator(ACCEPTED).validate(noAudience);

        assertEquals(1, result.getErrors().size());
        assertEquals(
                OAuth2ErrorCodes.INVALID_TOKEN,
                result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    void acceptsWhenAtLeastOneAudienceMatches() {
        OAuth2TokenValidatorResult result = new AudienceValidator(ACCEPTED)
                .validate(tokenWithAudience("nitrowater-web", "someone-else"));

        assertTrue(result.getErrors().isEmpty(), "intersection non-empty => accepted");
    }

    @Test
    void failsFastWhenNoAudienceConfigured() {
        assertThrows(IllegalArgumentException.class, () -> new AudienceValidator(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new AudienceValidator(null));
    }
}
