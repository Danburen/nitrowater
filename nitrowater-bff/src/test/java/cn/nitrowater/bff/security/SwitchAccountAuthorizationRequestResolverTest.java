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
 * Locks the resolver contract: {@code ?switch=1} must reach the AS as {@code prompt=login} in the
 * actual redirect URI, and {@code ?dfp=} must be parked in the session for the success handler.
 */
class SwitchAccountAuthorizationRequestResolverTest {

    private static final String FINGERPRINT = "0123456789abcdef";

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

    @Test
    void keepsTheFingerprintInTheSessionForTheSuccessHandler() {
        OAuth2AuthorizationRequestResolver delegate = mock(OAuth2AuthorizationRequestResolver.class);
        when(delegate.resolve(any(HttpServletRequest.class))).thenReturn(authorizationRequest());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter(SwitchAccountAuthorizationRequestResolver.DEVICE_PARAMETER, FINGERPRINT);

        new SwitchAccountAuthorizationRequestResolver(delegate).resolve(servletRequest);

        assertThat(servletRequest.getSession().getAttribute(SessionGuardFilter.PENDING_DEVICE_ATTRIBUTE))
                .isEqualTo(FINGERPRINT);
    }

    @Test
    void ignoresValuesThatDoNotLookLikeAFingerprint() {
        OAuth2AuthorizationRequestResolver delegate = mock(OAuth2AuthorizationRequestResolver.class);
        when(delegate.resolve(any(HttpServletRequest.class))).thenReturn(authorizationRequest());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter(SwitchAccountAuthorizationRequestResolver.DEVICE_PARAMETER, "not a fingerprint");

        new SwitchAccountAuthorizationRequestResolver(delegate).resolve(servletRequest);

        assertThat(servletRequest.getSession().getAttribute(SessionGuardFilter.PENDING_DEVICE_ATTRIBUTE)).isNull();
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
