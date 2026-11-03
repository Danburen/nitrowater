package cn.nitrowater.server.web.exception;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.infra.exception.SandboxException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps {@link SandboxException} to the shared {@link ApiResponse} error envelope. */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.web")
public class SandboxExceptionHandler {

    /**
     * @param ex the sandbox failure
     * @return {@code success=false} with {@link SandboxException#getStatus()}
     */
    @ExceptionHandler(SandboxException.class)
    public ResponseEntity<ApiResponse<Void>> handle(SandboxException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.reject(ex.getCode(), ex.getMessage(), null));
    }
}
