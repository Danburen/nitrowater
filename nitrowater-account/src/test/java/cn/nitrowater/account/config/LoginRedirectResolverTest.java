package cn.nitrowater.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A saved authorization URL is a one-shot resumption target, not a permanent login destination. */
class LoginRedirectResolverTest {

    @Test
    void consumesTheSavedRequestItResumes() {
        RequestCache cache = mock(RequestCache.class);
        SavedRequest saved = mock(SavedRequest.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login-by-code");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String authorizeUrl = "http://localhost:8090/oauth2/authorize?prompt=login&state=abc";
        when(cache.getRequest(request, response)).thenReturn(saved);
        when(saved.getRedirectUrl()).thenReturn(authorizeUrl);

        String target = new LoginRedirectResolver(cache, "http://localhost:5173").resolve(request, response);

        assertThat(target).isEqualTo(authorizeUrl);
        verify(cache).removeRequest(request, response);
    }

    @Test
    void fallsBackToTheSafeExplicitRedirect() {
        RequestCache cache = mock(RequestCache.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login-by-code");
        request.setParameter("redirect", "/runner?tab=2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String target = new LoginRedirectResolver(cache, "http://localhost:5173").resolve(request, response);

        assertThat(target).isEqualTo("/runner?tab=2");
    }
}
