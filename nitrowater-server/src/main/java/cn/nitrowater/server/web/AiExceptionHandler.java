package cn.nitrowater.server.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.ai.AiQuotaExceededException;
import cn.nitrowater.server.ai.moderation.ContentBlockedException;
import cn.nitrowater.server.ai.moderation.ContentCooldownException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * Maps AI rejections to a JSON body the web client can act on.
 *
 * <p>Each of these exceptions is already a plain framework status, but the default body carries no
 * machine-readable reason. Adding {@code error} lets the composer distinguish "daily budget is
 * gone" ({@code token_quota}), "the question was flagged" ({@code content_blocked}) and "the AI
 * endpoints are paused" ({@code content_cooldown}) without parsing prose.</p>
 */
@RestControllerAdvice(basePackages = "cn.nitrowater.server.web")
public class AiExceptionHandler {

    /**
     * @param ex the quota rejection
     * @return {@code success=false, code, message} with status 429
     */
    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AiQuotaExceededException ex) {
        return body(ex.getStatusCode().value(), ex.code(),
                Objects.requireNonNullElse(ex.getReason(), "今日 AI token 额度已用完，请明天再试"));
    }

    /**
     * @param ex the content-gate rejection
     * @return {@code success=false, code, message} with status 400
     */
    @ExceptionHandler(ContentBlockedException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ContentBlockedException ex) {
        return body(ex.getStatusCode().value(), ContentBlockedException.CODE,
                Objects.requireNonNullElse(ex.getReason(), "提问包含不适宜内容，已被拦截"));
    }

    /**
     * @param ex the risk-control cooldown rejection
     * @return {@code success=false, code, message} with status 429
     */
    @ExceptionHandler(ContentCooldownException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ContentCooldownException ex) {
        return body(ex.getStatusCode().value(), ContentCooldownException.CODE,
                Objects.requireNonNullElse(ex.getReason(), "AI 功能暂时停用，请稍后再试"));
    }

    private static ResponseEntity<ApiResponse<Void>> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.reject(code, message, null));
    }
}