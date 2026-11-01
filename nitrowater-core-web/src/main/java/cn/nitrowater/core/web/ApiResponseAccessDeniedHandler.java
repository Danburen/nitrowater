package cn.nitrowater.core.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Answers an authorized-but-forbidden API call with the shared {@link ApiResponse} envelope instead
 * of Spring Security's empty 403 body.
 */
public class ApiResponseAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiResponseWriter.error(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "无权限访问");
    }
}
