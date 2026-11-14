package cn.nitrowater.resource.infrastructure.exception;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.core.exception.threshold.RateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps resource/storage failures to the shared {@link ApiResponse} error envelope with the
 * intended HTTP status. Validation and unexpected failures are covered by
 * {@link cn.nitrowater.core.web.GlobalExceptionHandler}.
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.resource")
public class ResourceExceptionHandler {

    /**
     * @param ex resource-layer failure
     * @return {@code success=false} with the exception's status and machine-readable code
     */
    @ExceptionHandler(ResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleResource(ResourceException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResponse.reject(ex.code(), ex.getMessage(), null));
    }

    /**
     * @param ex storage-layer failure
     * @return {@code success=false} with 502
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Object>> handleStorage(StorageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.reject("storage_error", ex.getMessage(), null));
    }

    /**
     * {@code @RateLimit} rejection. Without this handler the generic
     * {@link cn.nitrowater.core.web.GlobalExceptionHandler} fallback would answer 500, which would
     * tell the client to retry instead of backing off.
     *
     * @param ex limiter rejection
     * @return 429 with the same generic code every bucket uses
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.reject("TOO_MANY_REQUEST", "TOO_MANY_REQUEST", null));
    }
}
