package cn.nitrowater.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the session-backed store keeps one token set per account and writes the map only after
 * the change (the service flushes on {@code setAttribute}, so a pre-change write would be lost).
 */
class MultiAccountAuthorizedClientRepositoryTest {

    private final MultiAccountAuthorizedClientRepository repository = new MultiAccountAuthorizedClientRepository();

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void savesAndLoadsOneTokenSetPerAccount() {
        repository.saveAuthorizedClient(client("9000000001", "token-a"), principal("9000000001"), request, response);
        repository.saveAuthorizedClient(client("9000000002", "token-b"), principal("9000000002"), request, response);

        assertThat(tokenOf(repository.loadAuthorizedClient("nitrowater", principal("9000000001"), request)))
                .isEqualTo("token-a");
        assertThat(tokenOf(repository.loadAuthorizedClient("nitrowater", principal("9000000002"), request)))
                .isEqualTo("token-b");
    }

    @Test
    void removingOneAccountKeepsTheOthers() {
        repository.saveAuthorizedClient(client("9000000001", "token-a"), principal("9000000001"), request, response);
        repository.saveAuthorizedClient(client("9000000002", "token-b"), principal("9000000002"), request, response);

        repository.removeAuthorizedClient("nitrowater", principal("9000000001"), request, response);

        OAuth2AuthorizedClient removed =
                repository.loadAuthorizedClient("nitrowater", principal("9000000001"), request);
        assertThat(removed).isNull();
        assertThat(tokenOf(repository.loadAuthorizedClient("nitrowater", principal("9000000002"), request)))
                .isEqualTo("token-b");
    }

    @Test
    void replacingATokenSetKeepsTheNewOne() {
        repository.saveAuthorizedClient(client("9000000001", "old"), principal("9000000001"), request, response);
        repository.saveAuthorizedClient(client("9000000001", "rotated"), principal("9000000001"), request, response);

        assertThat(tokenOf(repository.loadAuthorizedClient("nitrowater", principal("9000000001"), request)))
                .isEqualTo("rotated");
    }

    private static String tokenOf(OAuth2AuthorizedClient client) {
        return client == null ? null : client.getAccessToken().getTokenValue();
    }

    private static Authentication principal(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, java.util.List.of());
    }

    private static OAuth2AuthorizedClient client(String principalName, String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("nitrowater")
                .clientId("nitrowater-bff")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/nitrowater")
                .authorizationUri("http://localhost:8090/oauth2/authorize")
                .tokenUri("http://localhost:8090/oauth2/token")
                .scope("openid")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                Instant.now(), Instant.now().plusSeconds(600));
        return new OAuth2AuthorizedClient(registration, principalName, token);
    }
}
