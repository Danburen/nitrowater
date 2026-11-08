package cn.nitrowater.server.web.exception;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.infra.exception.CodedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * Maps AI rejections to a JSON body the web client can act on.
 *
 * <p>Every AI rejection is a {@link CodedException}, i.e. it already carries its HTTP status and its
 * stable code, so this advice has one rule instead of one method per type. Adding a rejection
 * therefore touches the exception class only, while the composer keeps switching on {@code code}
 * ({@code token_quota}, {@code content_blocked}, {@code content_cooldown}, {@code turn_timeout}).</p>
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.web")
public class AiExceptionHandler {

    /**
     * @param ex the coded rejection
     * @return {@code success=false, code, message} with the exception's own status
     */
    @ExceptionHandler(CodedException.class)
    public ResponseEntity<ApiResponse<Void>> handle(CodedException ex) {
        return body(ex.getStatusCode().value(), ex.code(),
                Objects.requireNonNullElse(ex.getReason(), "请求无法完成"));
    }

    private static ResponseEntity<ApiResponse<Void>> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.reject(code, message, null));
    }
}
