package cn.nitrowater.bff.security;

import cn.nitrowater.bff.web.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers unauthenticated {@code /api/**} requests with a JSON body instead of an empty 401.
 *
 * <p>The SPA needs a machine-readable signal to tell "the session is gone" from "this endpoint is
 * forbidden", and the reason header distinguishes a lost session from a version-invalidated one.
 * The stale session cookie is deleted at the same time so the browser stops replaying a dead id.</p>
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(SessionEpochFilter.REASON_ATTRIBUTE);
        String code = reason instanceof String value ? value : "session_expired";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Auth-Reason", code);
        response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
        response.getWriter().write("{\"error\":\"session_expired\",\"reason\":\"" + code
                + "\",\"message\":\"登录已失效，请重新登录\"}");
    }
}
