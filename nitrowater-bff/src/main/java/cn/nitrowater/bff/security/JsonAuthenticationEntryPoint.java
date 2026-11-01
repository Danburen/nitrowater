package cn.nitrowater.bff.security;

import cn.nitrowater.bff.web.SessionCookies;
import cn.nitrowater.core.web.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Map;

/**
 * Answers unauthenticated requests with the shared {@code ApiResponse} envelope instead of an empty
 * 401.
 *
 * <p>The SPA needs a machine-readable signal to tell "the session is gone" from "this endpoint is
 * forbidden", and the reason header distinguishes a lost session from a version-invalidated one.
 * The stale session cookie is deleted at the same time so the browser stops replaying a dead id.</p>
 *
 * <p>Headers are written before the body: the writer commits the response, and a header set after
 * that would be ignored.</p>
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(SessionEpochFilter.REASON_ATTRIBUTE);
        String code = reason instanceof String value ? value : "session_expired";
        response.setHeader("X-Auth-Reason", code);
        response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
        ApiResponseWriter.error(response, HttpServletResponse.SC_UNAUTHORIZED, "session_expired",
                "登录已失效，请重新登录", Map.of("reason", code));
    }
}
