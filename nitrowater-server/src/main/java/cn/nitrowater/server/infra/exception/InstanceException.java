package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;

/**
 * A rejection from the instance / remote-monitoring API, carrying one of the {@link InstanceCodes}.
 *
 * <p>Follows the project-wide {@link CodedException} convention: the stable code is written to the
 * JSON body by {@code AiExceptionHandler} (which covers the whole {@code cn.nitrowater.server.web}
 * package) and the HTTP status only serves infrastructure. Each factory below fixes both, so callers
 * cannot accidentally pair a code with the wrong status.</p>
 */
public class InstanceException extends CodedException {

    private InstanceException(HttpStatus status, String code, String reason) {
        super(status, code, reason);
    }

    /** @return a 400 rejection for a malformed / unsupported request protocol */
    public static InstanceException badProtocol(String reason) {
        return new InstanceException(HttpStatus.BAD_REQUEST, InstanceCodes.BAD_PROTOCOL, reason);
    }

    /** @return a 401 rejection for a missing, malformed or unknown api-key */
    public static InstanceException unauthorized(String reason) {
        return new InstanceException(HttpStatus.UNAUTHORIZED, InstanceCodes.UNAUTHORIZED, reason);
    }

    /** @return a 403 rejection when the authenticated key lacks the required scope */
    public static InstanceException forbidden(String reason) {
        return new InstanceException(HttpStatus.FORBIDDEN, InstanceCodes.FORBIDDEN, reason);
    }

    /** @return a 403 rejection when a key already owns the maximum number of instances */
    public static InstanceException limitExceeded(String reason) {
        return new InstanceException(HttpStatus.FORBIDDEN, InstanceCodes.LIMIT_EXCEEDED, reason);
    }

    /** @return a 409 rejection when an instance id already has a live command stream */
    public static InstanceException conflict(String reason) {
        return new InstanceException(HttpStatus.CONFLICT, InstanceCodes.CONFLICT, reason);
    }

    /** @return a 429 rejection when an instance exceeds its report rate */
    public static InstanceException rateLimited(String reason) {
        return new InstanceException(HttpStatus.TOO_MANY_REQUESTS, InstanceCodes.RATE_LIMITED, reason);
    }

    /** @return a 503 rejection when the target instance has no live command stream */
    public static InstanceException offline(String reason) {
        return new InstanceException(HttpStatus.SERVICE_UNAVAILABLE, InstanceCodes.OFFLINE, reason);
    }

    /** @return a 404 rejection when a credential or instance cannot be found */
    public static InstanceException notFound(String reason) {
        return new InstanceException(HttpStatus.NOT_FOUND, "not_found", reason);
    }
}
