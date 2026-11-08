package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * A rejection that also carries a stable machine-readable {@code code}.
 *
 * <p>The code is written to two places: the JSON body ({@code AiExceptionHandler}) and the SSE
 * {@code error} frame ({@code AgentTurnService}). Both read it from here, so adding a rejection is
 * one new subclass — neither writer needs a new branch. Encoding the vocabulary as an if-chain or a
 * per-type handler instead means a new type silently degrades to "no code" until someone remembers
 * to edit every writer.</p>
 *
 * <p>Subclasses are still {@link ResponseStatusException}s, so an endpoint that has no advice at all
 * answers with the right status even before the body is enriched.</p>
 */
public abstract class CodedException extends ResponseStatusException {

    private final String code;

    /**
     * @param status HTTP status the client should see
     * @param code   stable machine-readable code, mirrored by the web client
     * @param reason user-facing explanation
     */
    protected CodedException(HttpStatus status, String code, String reason) {
        super(status, reason);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** @return the stable machine-readable code carried by every representation */
    public String code() {
        return code;
    }

    /**
     * A streamed rejection is reported through an {@code error} frame, where the
     * {@code "429 TOO_MANY_REQUESTS"} prefix of the default message would only be noise.
     *
     * @return the user-facing reason
     */
    @Override
    public String getMessage() {
        return Objects.requireNonNullElse(getReason(), super.getMessage());
    }
}
