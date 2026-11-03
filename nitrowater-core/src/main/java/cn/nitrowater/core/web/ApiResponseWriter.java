package cn.nitrowater.core.web;

import cn.nitrowater.lib.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes the {@link ApiResponse} envelope from places that answer <b>outside</b> Spring MVC — a
 * {@code HandlerInterceptor} rejecting before a controller runs, or a {@code Filter} refusing the
 * request — where {@link ApiResponseAdvice} never sees the body.
 *
 * <p>Those replies used to be hand-concatenated JSON, which is both a different shape for clients
 * and a quoting hazard. Serializing through Jackson keeps them identical to every other endpoint.</p>
 */
public final class ApiResponseWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ApiResponseWriter() {
    }

    /**
     * @param response servlet response
     * @param status   HTTP status to send
     * @param code     machine-readable error code
     * @param message  user-facing message
     */
    public static void error(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        error(response, status, code, message, null);
    }

    /**
     * @param response servlet response
     * @param status   HTTP status to send
     * @param code     machine-readable error code
     * @param message  user-facing message
     * @param data     optional detail payload (e.g. quota counters) exposed as {@code data}
     */
    public static void error(HttpServletResponse response, int status, String code, String message, Object data)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(ApiResponse.reject(code, message, data)));
    }
}
