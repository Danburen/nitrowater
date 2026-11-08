package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a question is rejected by the content gate.
 *
 * <p>Mapped by {@code AiExceptionHandler} to a 400 body carrying {@link #CODE}, so the client can
 * show a specific message instead of parsing prose.</p>
 */
public class ContentBlockedException extends CodedException {

    /** Stable machine-readable code for the rejection body. */
    public static final String CODE = "content_blocked";

    /**
     * @param reason user-facing explanation
     */
    public ContentBlockedException(String reason) {
        super(HttpStatus.BAD_REQUEST, CODE, reason);
    }
}
