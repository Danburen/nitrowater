package cn.nitrowater.server.sandbox.web;

import cn.nitrowater.server.sandbox.SandboxException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps {@link SandboxException} to a JSON error body with the intended HTTP status. */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.sandbox")
public class SandboxExceptionHandler {

    /**
     * @param ex the sandbox failure
     * @return {@code {error, message}} with {@link SandboxException#status()}
     */
    @ExceptionHandler(SandboxException.class)
    public ResponseEntity<Map<String, Object>> handle(SandboxException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.code());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.status()).body(body);
    }
}
