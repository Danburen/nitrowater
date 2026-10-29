package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks the {@code ?switch=1} contract: the flag must reach the AS as {@code prompt=login} in the
 * actual redirect URI, not merely in the stored additional parameters.
 */
class SwitchAccountAuthorizationRequestResolverTest {

    @Test
    void addsPromptLoginToAuthorizationUri() {
        OAuth2AuthorizationRequest original = authorizationRequest();
        OAuth2AuthorizationRequestResolver delegate = mock(OAuth2AuthorizationRequestResolver.class);
        when(delegate.resolve(any(HttpServletRequest.class))).thenReturn(original);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("switch", "1");

        OAuth2AuthorizationRequest resolved =
                new SwitchAccountAuthorizationRequestResolver(delegate).resolve(servletRequest);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getAuthorizationRequestUri())
                .contains("response_type=code")
                .contains("prompt=login");
        assertThat(resolved.getAdditionalParameters()).containsEntry("prompt", "login");
    }

    @Test
    void leavesNormalAuthorizationUntouched() {
        OAuth2AuthorizationRequest original = authorizationRequest();
        OAuth2AuthorizationRequestResolver delegate = mock(OAuth2AuthorizationRequestResolver.class);
        when(delegate.resolve(any(HttpServletRequest.class))).thenReturn(original);

        OAuth2AuthorizationRequest resolved =
                new SwitchAccountAuthorizationRequestResolver(delegate).resolve(new MockHttpServletRequest());

        assertThat(resolved).isSameAs(original);
    }

    private static OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://as.example.com/oauth2/authorize")
                .clientId("nitrowater")
                .redirectUri("https://bff.example.com/login/oauth2/code/nitrowater")
                .state("state")
                .authorizationRequestUri("https://as.example.com/oauth2/authorize"
                        + "?response_type=code&client_id=nitrowater&state=state")
                .build();
    }
}
