package cn.nitrowater.server.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raised when the authenticated user has no daily token budget left.
 *
 * <p>It is a {@link ResponseStatusException} with status 429, so any endpoint maps it to a
 * rate-limit response even without a dedicated handler. {@link cn.nitrowater.server.web.AiExceptionHandler}
 * adds the machine-readable {@link #CODE} to the JSON body, and the streaming endpoints repeat the
 * same code in their {@code error} event, which is what the web client uses to switch the composer
 * into its exhausted state.</p>
 */
public class AiQuotaExceededException extends ResponseStatusException {

    /** Stable code shared by the JSON error body and the SSE error payload. */
    public static final String CODE = "token_quota";

    /**
     * @param reason user-facing explanation, sent verbatim to the client
     */
    public AiQuotaExceededException(String reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
    }
}
