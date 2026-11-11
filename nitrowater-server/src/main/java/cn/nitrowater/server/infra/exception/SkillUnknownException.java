package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a turn asks for a skill that is not in the catalog.
 *
 * <p>Mapped by {@code AiExceptionHandler} to a 400 body carrying {@link #CODE}, and by the SSE
 * {@code error} frame through {@link CodedException#code()}, so the picker can refresh its catalog
 * instead of showing prose.</p>
 */
public class SkillUnknownException extends CodedException {

    /** Stable machine-readable code for the rejection body. */
    public static final String CODE = "skill_unknown";

    /**
     * @param name the requested skill name
     */
    public SkillUnknownException(String name) {
        super(HttpStatus.BAD_REQUEST, CODE, "Unknown skill: " + name);
    }
}
