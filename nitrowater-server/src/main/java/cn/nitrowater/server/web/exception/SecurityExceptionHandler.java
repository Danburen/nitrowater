package cn.nitrowater.server.web.exception;

import cn.nitrowater.lib.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates method-security denials raised inside a controller into a 403 envelope.
 *
 * <p>Without this, {@code @PreAuthorize} failures are caught by the fallback
 * {@link cn.nitrowater.core.web.GlobalExceptionHandler} as a generic {@link RuntimeException} and
 * reported as 500.</p>
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.web")
public class SecurityExceptionHandler {

    /**
     * @param ex the denial raised by {@code @PreAuthorize}
     * @return 403 with {@code success=false}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.reject("forbidden", "无权限访问", null));
    }
}
