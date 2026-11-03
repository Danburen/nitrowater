package cn.nitrowater.bff.config;

import cn.nitrowater.bff.security.MultiAccountAuthorizedClientRepository;
import cn.nitrowater.bff.security.SwitchAccountAuthorizationRequestResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Server-side token handling.
 *
 * <p>{@code OAuth2AuthorizedClientManager} loads the authorized client from the session and,
 * because a {@code refreshToken} provider is registered, transparently refreshes an expired
 * access token with the refresh token before a business call. This is the mechanism that
 * replaces the browser-side iframe silent renew.</p>
 */
@Slf4j
@Configuration
public class OAuth2ClientConfig {

    /** Persists AT/RT in the (Redis-backed) HTTP session, keyed by registration + account subject. */
    @Bean
    public MultiAccountAuthorizedClientRepository authorizedClientRepository() {
        return new MultiAccountAuthorizedClientRepository();
    }

    /**
     * Decorates the standard resolver so {@code ?switch=1} becomes {@code prompt=login}.
     *
     * <p>Without this, the Authorization Server would silently reuse the current SSO session and
     * the user could never add a second account.</p>
     */
    @Bean
    public OAuth2AuthorizationRequestResolver switchAccountAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new SwitchAccountAuthorizationRequestResolver(
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI));
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();
        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(provider);
        // Do NOT keep the framework default (RemoveAuthorizedClientOAuth2AuthorizationFailureHandler):
        // it deletes the authorized client on invalid_grant/invalid_token and leaves the session
        // authenticated but unable to call upstream — every later request then failed with a 500.
        // Deciding what a rejected refresh means belongs to BffTokenService, which either recovers a
        // token a concurrent request just rotated or drops the whole session and answers 401.
        manager.setAuthorizationFailureHandler((ex, principal, attributes) -> log.warn(
                "OAuth2 authorization failed for '{}': {}",
                principal == null ? "unknown" : principal.getName(), ex.getError()));
        return manager;
    }
}
