package cn.nitrowater.server.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Rejects an access token whose {@code aud} claim does not intersect the audiences this
 * resource server is willing to serve.
 *
 * <p>Spring Security's default JWT validation covers {@code exp} / {@code nbf} and, when
 * {@code issuer-uri} is configured, {@code iss}. It deliberately does <strong>not</strong>
 * check {@code aud}, so without this validator any token minted by the Authorization Server
 * for <em>any</em> registered client would be accepted here. Audience restriction is what
 * binds a token to the API it may actually be used on.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3">RFC 7519 §4.1.3</a>
 */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The required audience is missing",
            null);

    private final List<String> acceptedAudiences;

    /**
     * @param acceptedAudiences audiences accepted by this resource server; must not be empty —
     *                          an empty set would mean "reject everything", which is almost
     *                          certainly a configuration mistake and should fail at startup
     *                          rather than surface as a blanket 401 at runtime
     */
    AudienceValidator(Collection<String> acceptedAudiences) {
        if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted audience must be configured");
        }
        this.acceptedAudiences = List.copyOf(acceptedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        // Jwt.getAudience() returns null (not an empty list) when the claim is absent
        List<String> tokenAudiences = token.getAudience();
        if (tokenAudiences != null) {
            for (String accepted : acceptedAudiences) {
                if (tokenAudiences.contains(accepted)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }
        return OAuth2TokenValidatorResult.failure(MISSING_AUDIENCE);
    }
}
