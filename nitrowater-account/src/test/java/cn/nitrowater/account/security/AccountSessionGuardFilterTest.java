package cn.nitrowater.account.security;

import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Guards the SSO session limits on the non-OIDC chain (admin console + account APIs). */
class AccountSessionGuardFilterTest {

    private final SessionEpochStore epochs = mock(SessionEpochStore.class);

    private final AccountSessionGuardFilter filter =
            new AccountSessionGuardFilter(this.epochs, Duration.ofDays(30));

    @BeforeEach
    void currentEpoch() {
        when(this.epochs.current()).thenReturn("epoch-1");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesSessionsFromAnOlderEpoch() throws Exception {
        MockHttpSession session = loggedInDaysAgo(1);
        session.setAttribute(AccountSessionGuardFilter.EPOCH_ATTRIBUTE, "epoch-0");
        MockHttpServletRequest request = request(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void invalidatesSessionsOlderThanTheAbsoluteCap() throws Exception {
        MockHttpSession session = loggedInDaysAgo(31);
        MockHttpServletRequest request = request(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void keepsAFreshSession() throws Exception {
        MockHttpSession session = loggedInDaysAgo(2);
        MockHttpServletRequest request = request(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void ignoresNonSessionAuthentications() throws Exception {
        // A bearer JWT has its own expiry; it must not be judged by the browser session's age.
        MockHttpSession session = loggedInDaysAgo(31);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "n/a", List.of()));
        MockHttpServletRequest request = request(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    /** @return a session authenticated {@code days} days ago under the current epoch */
    private static MockHttpSession loggedInDaysAgo(int days) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AccountSessionGuardFilter.EPOCH_ATTRIBUTE, "epoch-1");
        session.setAttribute(PromptLoginFilter.AUTH_AT_ATTRIBUTE, Instant.now().minus(Duration.ofDays(days)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mock(SsoUserPrincipal.class), "n/a", List.of()));
        return session;
    }

    private static MockHttpServletRequest request(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/session");
        request.setSession(session);
        return request;
    }
}
