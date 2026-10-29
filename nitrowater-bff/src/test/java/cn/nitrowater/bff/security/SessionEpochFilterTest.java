package cn.nitrowater.bff.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the deploy-time session invalidation contract. */
class SessionEpochFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesSessionsCreatedByAPreviousDeployment() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionEpochFilter.EPOCH_ATTRIBUTE, "old-release");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        authenticate();

        new SessionEpochFilter("new-release").doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(SessionEpochFilter.REASON_ATTRIBUTE)).isEqualTo("session_epoch_mismatch");
    }

    @Test
    void keepsSessionsCreatedByTheCurrentDeployment() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionEpochFilter.EPOCH_ATTRIBUTE, "same-release");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        authenticate();

        new SessionEpochFilter("same-release").doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void ignoresAnonymousSessionsThatHaveNoEpochYet() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        new SessionEpochFilter("any-release").doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "n/a", List.of()));
    }
}
