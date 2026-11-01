package cn.nitrowater.account.web;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.core.exception.BizException;
import tools.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Global exception handler — maps exceptions thrown by the auth controllers to the unified
 * {@link ApiResponse} envelope ({@code success/code/message/data}) so the (static and
 * SPA) clients can surface meaningful messages instead of Spring's default error page.
 *
 * <p>i18n keys resolve through the core {@code messages*.properties} bundles.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource msgSrc;

    public GlobalExceptionHandler(MessageSource msgSrc) {
        this.msgSrc = msgSrc;
    }

    private static Locale locale() {
        return LocaleContextHolder.getLocale();
    }

    /**
     * SSE long-poll idle timeouts are a normal client disconnect; the response is already
     * committed as {@code text/event-stream}, so no JSON error may be written.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        log.debug("Async request timed out (SSE idle disconnect): {}", ex.getMessage());
    }

    /** {@code @RequestBody} bean-validation failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError.getField() + ": " + msgSrc.getMessage(fieldError, locale()));
        }
        return validationError(errors);
    }

    /** {@code @RequestParam} / method-level ({@code @Validated}) constraint violations. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String msg = msgSrc.getMessage(violation.getMessageTemplate(),
                    violation.getExecutableParameters(), violation.getMessage(), locale());
            errors.add(violation.getPropertyPath() + ": " + msg);
        }
        return validationError(errors);
    }

    /** Unreadable request body: malformed JSON, wrong enum value, type mismatch… */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty() ? "unknown" : ife.getPath().get(0).getPropertyName();
            String value = ife.getValue() != null ? ife.getValue().toString() : "null";
            List<String> allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .toList();
            String msg = msgSrc.getMessage("validation.enum.not_support",
                    new Object[]{field, value, allowed},
                    "Invalid value '" + value + "' for '" + field + "'; allowed: " + allowed, locale());
            return validationError(List.of(msg));
        }
        log.warn("Malformed request body: {}", ex.getMessage());
        String msg = msgSrc.getMessage("validation.malformed", null, "Request body format error", locale());
        return validationError(List.of(msg));
    }

    /** Auth failures (bad credentials, captcha, lockout…). */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth(AuthException ex) {
        String message = msgSrc.getMessage(ex.getMessage(), ex.getParams(), "Auth failed", locale());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.reject(ex.getErrorCode(), message, null));
    }

    /**
     * Business exceptions (incl. {@code BanForbiddenException}); the HTTP status comes from
     * the exception ({@link BizException#getHttpStatusCode()}).
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException ex) {
        String message = msgSrc.getMessage(ex.getMessage(), ex.getParams(), "error", locale());
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatusCode());
        return ResponseEntity.status(status != null ? status : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.reject(ex.getErrorCode(), message, null));
    }

    /** Preserve framework HTTP statuses (e.g. 404 for a missing static resource). */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Object>> handleErrorResponse(ErrorResponseException ex) {
        String detail = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.reject(String.valueOf(ex.getStatusCode().value()), detail, null));
    }

    /**
     * A missing route or static resource. Browsers (which ask for {@code text/html}) get the
     * branded 404 page served from {@code static/error/404.html}; every other client keeps the
     * standard {@link ApiResponse} envelope, so the JSON API contract is unchanged. Being more
     * specific than {@link #handleErrorResponse}, this handler wins for {@code NoResourceFoundException}.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        if (prefersHtml(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(new ClassPathResource("static/error/404.html"));
        }
        String detail = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.reject(String.valueOf(ex.getStatusCode().value()), detail, null));
    }

    /** True when the request's {@code Accept} header asks for HTML (i.e. a browser navigation). */
    private static boolean prefersHtml(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    /** Fallback: log and return a generic 500. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception: ", ex);
        String message = msgSrc.getMessage(BaseResponseCode.INTERNAL_SERVER_ERROR.getCode(), null,
                "Internal server error", locale());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.reject(BaseResponseCode.INTERNAL_SERVER_ERROR.getCode(), message, null));
    }

    private ResponseEntity<ApiResponse<Object>> validationError(List<String> errors) {
        String message = msgSrc.getMessage(BaseResponseCode.VALIDATION_ERROR.getCode(), null,
                "Parameter validation failed", locale());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.reject(BaseResponseCode.VALIDATION_ERROR.getCode(), message, errors));
    }
}
