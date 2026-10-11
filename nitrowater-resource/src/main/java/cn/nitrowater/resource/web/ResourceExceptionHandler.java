package cn.nitrowater.resource.web;

import cn.nitrowater.resource.service.ResourceException;
import cn.nitrowater.resource.storage.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
