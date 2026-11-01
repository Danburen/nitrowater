package cn.nitrowater.account.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Rejects an access token whose {@code aud} does not include the account API's expected audience.
 *
 * <p>The BFF calls the account API with the user's access token, so the account resource-server
 * chain must not accept tokens minted for unrelated clients.</p>
 */
final class AccountAudienceValidator implements OAuth2TokenValidator<@org.jetbrains.annotations.NotNull Jwt> {

    private static final OAuth2Error MISSING_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The required audience is missing", null);

    private final List<String> acceptedAudiences;

    AccountAudienceValidator(Collection<String> acceptedAudiences) {
        if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted audience must be configured");
        }
        this.acceptedAudiences = List.copyOf(acceptedAudiences);
    }

    @Override
    public @NotNull OAuth2TokenValidatorResult validate(Jwt token) {
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
