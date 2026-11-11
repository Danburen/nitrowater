package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a turn selects a skill while the administrator switched skills off.
 *
 * <p>Mapped by {@code AiExceptionHandler} to a 503 body carrying {@link #CODE}. A stale picker (or
 * a direct API call) hits this after the switch flips, which is why it is a coded rejection rather
 * than a silent no-op.</p>
 */
public class SkillsDisabledException extends CodedException {

    /** Stable machine-readable code for the rejection body. */
    public static final String CODE = "ai_skills_disabled";

    public SkillsDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, "AI skills are disabled");
    }
}
