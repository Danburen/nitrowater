package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.AccountSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Guards the anonymous probe used by the SPA to distinguish "never signed in" from "session lost". */
class SessionControllerTest {

    private final SessionController controller = new SessionController(mock(AccountSessionService.class));

    @Test
    void reportsALostSessionWhenTheBrowserStillSendsAStaleCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookies.NAME, "dead-session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> body = controller.me(null, null, request, response);

        assertThat(body).containsEntry("authenticated", false);
        assertThat(body).containsEntry("expired", true);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(SessionCookies.NAME + "=")
                .contains("Max-Age=0");
    }

    @Test
    void reportsAPlainAnonymousVisitWithoutSessionCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> body = controller.me(null, null, new MockHttpServletRequest(), response);

        assertThat(body).containsEntry("authenticated", false);
        assertThat(body).doesNotContainKey("expired");
    }
}
