package cn.nitrowater.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the two invariants that keep a rotated refresh token from becoming a permanent failure:
 * a session asks for re-authentication instead of leaking a 500, and a refresh already performed by
 * a concurrent request of the same session is reused rather than performed again.
 */
class BffTokenServiceTest {

    private final MultiAccountAuthorizedClientRepository repository = new MultiAccountAuthorizedClientRepository();

    @Test
    void anonymousPrincipalRequiresReauthentication() {
        BffTokenService service = service(mock(OAuth2AuthorizedClientManager.class));
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatThrownBy(() -> service.accessToken(anonymous, new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ReauthRequiredException.class,
                        ex -> assertThat(ex.reason()).isEqualTo("not_authenticated"));
    }

    @Test
    void missingAuthorizedClientDropsTheSession() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        assertThatThrownBy(() -> service(manager).accessToken(principal("9000000001"), request,
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ReauthRequiredException.class,
                        ex -> assertThat(ex.reason()).isEqualTo("no_authorized_client"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void rejectedRefreshDropsTheSessionInsteadOfLeavingItHalfAuthenticated() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenThrow(new ClientAuthorizationRequiredException("nitrowater"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        assertThatThrownBy(() -> service(manager).accessToken(principal("9000000001"), request,
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ReauthRequiredException.class,
                        ex -> assertThat(ex.reason()).isEqualTo("refresh_rejected"));
        assertThat(session.isInvalid()).isTrue();
    }

    /**
     * Two requests of one session arriving at access-token expiry: the second must adopt the first
     * one's rotated token. Calling the manager again would present the already-rotated refresh token
     * and the Authorization Server would answer invalid_grant.
     */
    @Test
    void reusesTheTokenARefreshInTheSameSessionJustProduced() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient rotated = client("rotated-token");
        when(manager.authorize(any())).thenReturn(rotated);
        BffTokenService service = service(manager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        Authentication principal = principal("9000000001");

        String first = service.accessToken(principal, request, new MockHttpServletResponse());
        String second = service.accessToken(principal, request, new MockHttpServletResponse());

        assertThat(first).isEqualTo("rotated-token");
        assertThat(second).isEqualTo("rotated-token");
        verify(manager, times(1)).authorize(any());
    }

    private BffTokenService service(OAuth2AuthorizedClientManager manager) {
        return new BffTokenService(manager, this.repository, "nitrowater");
    }

    private static Authentication principal(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }

    private static OAuth2AuthorizedClient client(String tokenValue) {
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                Instant.now(), Instant.now().plusSeconds(600));
        when(client.getAccessToken()).thenReturn(token);
        when(client.getClientRegistration()).thenReturn(ClientRegistration.withRegistrationId("nitrowater")
                .clientId("nitrowater-bff")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/nitrowater")
                .authorizationUri("http://localhost:8090/oauth2/authorize")
                .tokenUri("http://localhost:8090/oauth2/token")
                .scope("openid")
                .build());
        return client;
    }
}
