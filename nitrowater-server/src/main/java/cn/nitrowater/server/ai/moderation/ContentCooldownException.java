package cn.nitrowater.server.ai.moderation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raised while a user is in the risk-control cooldown.
 *
 * <p>Only repeated <em>input</em> hits lead here, and only for the AI endpoints: the rest of the
 * account keeps working. The cooldown expires on its own, so a false positive costs a pause rather
 * than an appeal.</p>
 */
public class ContentCooldownException extends ResponseStatusException {

    /** Stable machine-readable code for the rejection body. */
    public static final String CODE = "content_cooldown";

    /**
     * @param reason user-facing explanation
     */
    public ContentCooldownException(String reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
    }
}
