package cn.nitrowater.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the 401 body the SPA relies on to show its "session expired" notice. */
class JsonAuthenticationEntryPointTest {

    @Test
    void writesAMachineReadableBodyAndDeletesTheSessionCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SessionEpochFilter.REASON_ATTRIBUTE, "session_epoch_mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint().commence(request, response,
                new InsufficientAuthenticationException("no session"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Auth-Reason")).isEqualTo("session_epoch_mismatch");
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"session_expired\"");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("BFFSESSION=")
                .contains("Max-Age=0");
    }

    @Test
    void fallsBackToTheGenericReasonWhenNoFilterSetOne() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint().commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no session"));

        assertThat(response.getHeader("X-Auth-Reason")).isEqualTo("session_expired");
    }
}
