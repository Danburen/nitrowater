package cn.nitrowater.bff.security;

import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import jakarta.servlet.http.Cookie;
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

/** Guards the session lifetime contract: release epoch, absolute age, device fingerprint. */
class SessionGuardFilterTest {

    private static final String FINGERPRINT = "0123456789abcdef";

    private final SessionEpochStore epochs = mock(SessionEpochStore.class);

    private final SessionGuardFilter filter = new SessionGuardFilter(this.epochs, Duration.ofDays(30));

    @BeforeEach
    void currentEpoch() {
        when(this.epochs.current()).thenReturn("release-1");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesSessionsCreatedByAPreviousDeployment() throws Exception {
        MockHttpSession session = loggedInDaysAgo(1);
        session.setAttribute(SessionGuardFilter.EPOCH_ATTRIBUTE, "old-release");
        MockHttpServletRequest request = request(session, "/api/me");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getAttribute(SessionGuardFilter.REASON_ATTRIBUTE))
                .isEqualTo("session_epoch_mismatch");
    }

    @Test
    void invalidatesSessionsOlderThanTheAbsoluteCapHoweverActive() throws Exception {
        MockHttpSession session = loggedInDaysAgo(31);
        MockHttpServletRequest request = request(session, "/api/me");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getAttribute(SessionGuardFilter.REASON_ATTRIBUTE))
                .isEqualTo("session_expired");
    }

    @Test
    void keepsAnActiveSessionWithinTheCap() throws Exception {
        MockHttpSession session = loggedInDaysAgo(29);
        MockHttpServletRequest request = request(session, "/api/me");
        authenticate();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void invalidatesWhenTheFingerprintChanged() throws Exception {
        MockHttpSession session = boundTo(FINGERPRINT);
        MockHttpServletRequest request = request(session, "/api/me");
        request.addHeader(SessionGuardFilter.DEVICE_HEADER, "fedcba9876543210");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getAttribute(SessionGuardFilter.REASON_ATTRIBUTE)).isEqualTo("device_changed");
    }

    @Test
    void acceptsTheFingerprintUsedAtLogin() throws Exception {
        MockHttpSession session = boundTo(FINGERPRINT);
        MockHttpServletRequest request = request(session, "/api/me");
        request.addHeader(SessionGuardFilter.DEVICE_HEADER, FINGERPRINT);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void acceptsTheCookieForTopLevelNavigations() throws Exception {
        // `window.open('/api/.../download')` cannot set X-Device-Fp; the SPA mirrors the id into a
        // first-party cookie for exactly these requests.
        MockHttpSession session = boundTo(FINGERPRINT);
        MockHttpServletRequest request = request(session, "/api/admin/resources/x/download");
        request.setCookies(new Cookie(SessionGuardFilter.DEVICE_COOKIE, FINGERPRINT));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void doesNotDemandTheHeaderOnLogout() throws Exception {
        // RP-initiated logout is a top-level form POST and cannot carry a custom header.
        MockHttpSession session = boundTo(FINGERPRINT);
        MockHttpServletRequest request = request(session, "/logout");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void ignoresUnauthenticatedSessions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session, "/api/me");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    /** @return a session authenticated {@code days} days ago, current release, no device bound */
    private static MockHttpSession loggedInDaysAgo(int days) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionGuardFilter.EPOCH_ATTRIBUTE, "release-1");
        session.setAttribute(SessionGuardFilter.AUTH_AT_ATTRIBUTE,
                Instant.now().minus(Duration.ofDays(days)));
        authenticate();
        return session;
    }

    /** @return the same, plus a device hash captured at login */
    private static MockHttpSession boundTo(String fingerprint) {
        MockHttpSession session = loggedInDaysAgo(1);
        session.setAttribute(SessionGuardFilter.DEVICE_ATTRIBUTE,
                SessionGuardFilter.hashDevice(fingerprint));
        return session;
    }

    private static MockHttpServletRequest request(MockHttpSession session, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setSession(session);
        return request;
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "n/a", List.of()));
    }
}
