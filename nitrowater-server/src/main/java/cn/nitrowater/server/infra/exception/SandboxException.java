package cn.nitrowater.server.infra.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Signals a sandbox request that cannot be served, carrying the HTTP status to return.
 *
 * @see cn.nitrowater.server.service.sandbox.SandboxService
 */
@Getter
public class SandboxException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * @param status HTTP status to respond with
     * @param code a stable machine-readable error code (e.g. {@code code_too_large})
     * @param message a human-readable explanation
     */
    public SandboxException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
