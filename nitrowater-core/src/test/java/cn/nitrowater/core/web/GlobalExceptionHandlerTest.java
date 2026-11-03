package cn.nitrowater.core.web;

import cn.nitrowater.lib.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the gateway-style mapping of a failed outbound call.
 *
 * <p>Regression: a connection-refused downstream (business API on :8081 not running) used to fall
 * through to the {@link RuntimeException} fallback and surface as {@code 500 / internal_error}
 * "server internal error", logging an opaque {@code I/O error on GET request ...: null}. Clients
 * then blamed their own request instead of a missing dependency.</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unreachableUpstreamBecomes502WithUpstreamUnavailableCode() {
        ResourceAccessException ex = new ResourceAccessException(
                "I/O error on GET request for \"http://localhost:8081/api/public/plugins\": null",
                new ConnectException("Connection refused"));

        ResponseEntity<ApiResponse<Object>> response = handler.handleUpstreamUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("upstream_unavailable");
        assertThat(body.getMessage()).isNotBlank();
        assertThat(body.getData()).isNull();
    }

    @Test
    void unhandledRuntimeStillMapsTo500() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleRuntime(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("internal_error");
    }

    @Test
    void upstreamHandlerIsMoreSpecificThanTheRuntimeFallback() {
        // Spring picks the handler by exception-type specificity, so the 502 mapping only wins if
        // ResourceAccessException is declared on its own method rather than left to RuntimeException.
        boolean declared = java.util.Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .flatMap(method -> java.util.Arrays.stream(
                        method.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class) == null
                                ? new Class<?>[0]
                                : method.getAnnotation(
                                                org.springframework.web.bind.annotation.ExceptionHandler.class)
                                        .value()))
                .anyMatch(ResourceAccessException.class::equals);

        assertThat(declared).isTrue();
    }
}
