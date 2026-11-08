package cn.nitrowater.core.web;

import cn.nitrowater.lib.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Fallback exception handler shared by every API service: turns anything the domain handlers did
 * not claim into the same {@link ApiResponse} envelope, so clients never have to parse two error
 * shapes.
 *
 * <p>It is deliberately <b>last</b> in the ordering: a service advice handling a more specific
 * exception (resource/storage failures, AI quota, sandbox errors, Spring Security denials) wins by
 * exception-type specificity, and this class only picks up the rest. Services opt in with
 * {@code @Import(GlobalExceptionHandler.class)}.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * An SSE client walking away leaves the response half-committed; writing a JSON body here would
     * raise "no converter for ApiResponse with preset Content-Type text/event-stream". Nothing to do.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("Async request timed out (SSE client left): {}", ex.getMessage());
    }

    /**
     * Bean validation on a request body failed.
     *
     * @param ex validation failure carrying the offending fields
     * @return 400 with one {@code field: message} entry per violation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        String message = errors.isEmpty() ? "请求参数不合法" : String.join("; ", errors);
        return ResponseEntity.badRequest().body(ApiResponse.reject("invalid_request", message, errors));
    }

    /**
     * The request body could not be parsed (malformed JSON, wrong shape, bad enum value).
     *
     * @param ex parse failure
     * @return 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.reject("invalid_request", "请求体格式错误", null));
    }

    /**
     * The container could not parse a multipart request: a truncated body (for example a proxy
     * that drained the stream before forwarding it), a missing or duplicated boundary, or an
     * unreadable upload temp location.
     *
     * <p>This is a request/pipeline fault, not a server bug, so it must not fall through to
     * {@link #handleRuntime} and surface as {@code 500 internal_error}: the caller has to know the
     * upload itself was rejected. It also keeps a noisy stack trace out of the error log.</p>
     *
     * @param ex multipart parse failure
     * @return 400 with the shared envelope
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException ex) {
        log.warn("Malformed multipart request: {} | cause chain: {}", ex.getMessage(), describeCauseChain(ex));
        return ResponseEntity.badRequest()
                .body(ApiResponse.reject("invalid_multipart", "上传内容无法解析，请重试", null));
    }

    /**
     * A multipart upload exceeded {@code spring.servlet.multipart.max-file-size} /
     * {@code max-request-size} (the container aborts parsing and throws this).
     *
     * @param ex size-limit rejection
     * @return 413 with the shared envelope
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected, size limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiResponse.reject("upload_too_large", "上传文件超过大小限制", null));
    }

    /**
     * Deliberate status exceptions ({@code ResponseStatusException}, {@code ErrorResponseException})
     * must keep their status: without this they would be swallowed by the {@link RuntimeException}
     * fallback and reported as 500.
     *
     * @param ex the status-bearing exception
     * @return its own status carrying the envelope
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Object>> handleErrorResponse(ErrorResponseException ex) {
        String detail = ex.getBody().getDetail();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.reject("http_" + ex.getStatusCode().value(), detail, null));
    }

    /**
     * No route matched. Returned as JSON instead of the default Whitelabel error page so the SPA
     * always receives the envelope.
     *
     * @param ex missing resource
     * @return 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.reject("not_found", "接口不存在", null));
    }

    /**
     * An outbound HTTP dependency could not be reached: connection refused (service down), DNS
     * failure, connect/read timeout or TLS handshake error.
     *
     * <p>Without this handler these failures fall through to {@link #handleRuntime} and surface as a
     * 500 "服务器内部错误" with the opaque {@code I/O error on GET request ...: null} message, which
     * gives the caller no hint that the dependency — not the request — is at fault. 502 keeps the
     * gateway semantics and tells the client it is safe to retry later.</p>
     *
     * @param ex failed client HTTP call
     * @return 502 with the shared envelope
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleUpstreamUnavailable(ResourceAccessException ex) {
        log.warn("Upstream call failed: {} | cause chain: {}", ex.getMessage(), describeCauseChain(ex));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.reject("upstream_unavailable", "上游服务不可用，请稍后重试", null));
    }

    /**
     * Renders the cause chain of an exception as {@code Type: message <- Type: message}.
     *
     * <p>{@link ResourceAccessException} often carries a null message ("...: null"), so the useful
     * signal — {@code ConnectException}/{@code HttpTimeoutException} and its reason — lives in the
     * causes. Depth is capped defensively against a self-referential chain.</p>
     *
     * @param ex exception whose causes should be described
     * @return one-line cause chain
     */
    private static String describeCauseChain(Throwable ex) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (!text.isEmpty()) {
                text.append(" <- ");
            }
            text.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                text.append(": ").append(current.getMessage());
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return text.toString();
    }

    /**
     * Last resort: an unhandled exception must still produce the envelope, and the stack trace must
     * never reach the client.
     *
     * @param ex unhandled failure
     * @return 500
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.reject("internal_error", "服务器内部错误", null));
    }
}
