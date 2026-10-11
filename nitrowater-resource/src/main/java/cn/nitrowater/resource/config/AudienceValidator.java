package cn.nitrowater.resource.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Rejects an access token whose {@code aud} claim does not intersect the audiences this resource
 * server accepts, binding a token to the API it may be used on.
 *
 * <p>Spring's JWT validation covers {@code exp}/{@code nbf}/{@code iss} but not {@code aud}.</p>
 */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The required audience is missing", null);

    private final List<String> acceptedAudiences;

    /**
     * @param acceptedAudiences audiences accepted by this resource server; must not be empty
     */
    AudienceValidator(Collection<String> acceptedAudiences) {
        if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted audience must be configured");
        }
        this.acceptedAudiences = List.copyOf(acceptedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
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
