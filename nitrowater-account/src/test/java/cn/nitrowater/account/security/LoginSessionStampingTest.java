package cn.nitrowater.account.security;

import cn.nitrowater.account.service.security.AccountSessionGuardFilter;
import cn.nitrowater.account.service.security.PromptLoginFilter;
import cn.nitrowater.account.service.security.SsoSessionSignInService;
import cn.nitrowater.account.service.security.SsoUserPrincipal;
import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The JSON code-login path must stamp the same session attributes as the hosted form login, or a
 * resumed {@code prompt=login} authorization is treated as expired and bounces back to the login
 * page instead of returning the browser to the page that started the flow.
 */
class LoginSessionStampingTest {

    private final SessionEpochStore epochs = mock(SessionEpochStore.class);
    private final PromptLoginFilter filter =
            new PromptLoginFilter(mock(RequestCache.class), Duration.ofDays(30), this.epochs);

    @BeforeEach
    void currentEpoch() {
        when(this.epochs.current()).thenReturn("epoch-1");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void codeLoginStampsTheSessionLikeFormLogin() {
        SsoSessionSignInService service =
                new SsoSessionSignInService(mock(SessionRegistry.class), this.epochs);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login-by-code");

        service.signIn(request, new MockHttpServletResponse(), authenticated());

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(PromptLoginFilter.AUTH_AT_ATTRIBUTE)).isInstanceOf(Instant.class);
        assertThat(session.getAttribute(AccountSessionGuardFilter.EPOCH_ATTRIBUTE)).isEqualTo("epoch-1");
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();
    }

    @Test
    void resumedPromptLoginIsLetThroughWhenTheSessionWasStamped() throws Exception {
        MockHttpSession session = new MockHttpSession();
        PromptLoginFilter.stamp(session, this.epochs);
        PromptLoginFilter.markIfNeeded(session, "http://localhost:8090/oauth2/authorize?prompt=login&continue");
        SecurityContextHolder.getContext().setAuthentication(authenticated());
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(authorizeRequest(session), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(session.getAttribute(PromptLoginFilter.RESUMED_ATTRIBUTE)).isNull();
    }

    @Test
    void unstampedSessionStillBouncesBackToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        PromptLoginFilter.markIfNeeded(session, "http://localhost:8090/oauth2/authorize?prompt=login&continue");
        SecurityContextHolder.getContext().setAuthentication(authenticated());
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(authorizeRequest(session), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired=1");
    }

    private static Authentication authenticated() {
        return new UsernamePasswordAuthenticationToken(mock(SsoUserPrincipal.class), "n/a", List.of());
    }

    private static MockHttpServletRequest authorizeRequest(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setParameter("prompt", "login");
        request.addHeader("Accept", "text/html");
        request.setSession(session);
        return request;
    }
}
