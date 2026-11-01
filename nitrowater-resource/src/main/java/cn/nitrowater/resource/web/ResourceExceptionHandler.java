package cn.nitrowater.resource.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.infrastructure.StorageException;
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
}
