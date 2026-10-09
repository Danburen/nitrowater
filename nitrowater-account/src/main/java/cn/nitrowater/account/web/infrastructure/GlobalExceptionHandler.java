package cn.nitrowater.account.web.infrastructure;

import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.api.ErrorResponse;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.core.exception.BizException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Global exception handler — maps exceptions thrown by the auth controllers to the unified
 * {@link ErrorResponse} envelope ({@code code/message/errors/timestamp}) so the (static and
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
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError.getField() + ": " + msgSrc.getMessage(fieldError, locale()));
        }
        return validationError(errors);
    }

    /** {@code @RequestParam} / method-level ({@code @Validated}) constraint violations. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
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
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty() ? "unknown" : ife.getPath().get(0).getFieldName();
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
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        String message = msgSrc.getMessage(ex.getMessage(), ex.getParams(), "Auth failed", locale());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getErrorCode(), message, null, new Date()));
    }

    /**
     * Business exceptions (incl. {@code BanForbiddenException}); the HTTP status comes from
     * the exception ({@link BizException#getHttpStatusCode()}).
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResponse> handleBiz(BizException ex) {
        String message = msgSrc.getMessage(ex.getMessage(), ex.getParams(), "error", locale());
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatusCode());
        return ResponseEntity.status(status != null ? status : HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrorCode(), message, null, new Date()));
    }

    /** Preserve framework HTTP statuses (e.g. 404 for a missing static resource). */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponse(ErrorResponseException ex) {
        String detail = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(String.valueOf(ex.getStatusCode().value()), detail, null, new Date()));
    }

    /** Fallback: log and return a generic 500. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception: ", ex);
        String message = msgSrc.getMessage(BaseResponseCode.INTERNAL_SERVER_ERROR.getCode(), null,
                "Internal server error", locale());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(BaseResponseCode.INTERNAL_SERVER_ERROR.getCode(), message, null, new Date()));
    }

    private ResponseEntity<ErrorResponse> validationError(List<String> errors) {
        String message = msgSrc.getMessage(BaseResponseCode.VALIDATION_ERROR.getCode(), null,
                "Parameter validation failed", locale());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(BaseResponseCode.VALIDATION_ERROR.getCode(), message, errors, new Date()));
    }
}
