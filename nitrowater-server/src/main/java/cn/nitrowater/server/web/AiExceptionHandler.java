package cn.nitrowater.server.web;

import cn.nitrowater.server.ai.AiQuotaExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps AI quota exhaustion to a JSON body the web client can act on.
 *
 * <p>{@link AiQuotaExceededException} is already a 429, but the plain framework body carries no
 * machine-readable reason. Adding {@code error: token_quota} lets the composer distinguish "daily
 * budget is gone" from the per-minute {@code rate_limited} rejection without parsing prose.</p>
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.web")
public class AiExceptionHandler {

    /**
     * @param ex the quota rejection
     * @return {@code {error, message}} with status 429
     */
    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handle(AiQuotaExceededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", AiQuotaExceededException.CODE);
        body.put("message", Objects.requireNonNullElse(ex.getReason(), "今日 AI token 额度已用完，请明天再试"));
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
