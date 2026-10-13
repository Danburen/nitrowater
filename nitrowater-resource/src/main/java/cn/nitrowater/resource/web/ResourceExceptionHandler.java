package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.infrastructure.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  Maps resource/storage failures to a JSON error body with the intended HTTP status.
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.resource")
public class ResourceExceptionHandler {

    /**
     * @param ex resource-layer failure
     * @return {@code {error, message}} with the exception's status
     */
    @ExceptionHandler(ResourceException.class)
    public ResponseEntity<Map<String, Object>> handleResource(ResourceException ex) {
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    /**
     * @param ex storage-layer failure
     * @return {@code {error, message}} with 502
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(StorageException ex) {
        return body(HttpStatus.BAD_GATEWAY, "storage_error", ex.getMessage());
    }

    /**
     * Maps bean-validation failures on a request body to 400 instead of a generic 500,
     * listing the offending fields (e.g. an oversized {@code app} beyond its column width).
     *
     * @param ex request-body validation failure
     * @return {@code {error, message}} with 400 and a field-error summary
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, "invalid_request", message.isBlank() ? "Invalid request" : message);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
