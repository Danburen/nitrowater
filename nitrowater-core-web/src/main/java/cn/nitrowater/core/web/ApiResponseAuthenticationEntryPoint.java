package cn.nitrowater.core.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers an unauthenticated API call with the shared {@link ApiResponse} envelope.
 *
 * <p>Spring Security's default entry point returns an empty 401 body. Because this project's public
 * contract is "every JSON error is {@code {success:false, code, message, data}}", the SPA would
 * otherwise have to special-case 401/403. Configure it with
 * {@code http.exceptionHandling(e -> e.authenticationEntryPoint(new ApiResponseAuthenticationEntryPoint()))}.</p>
 */
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ApiResponseWriter.error(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized",
                "未登录或登录已过期");
    }
}
