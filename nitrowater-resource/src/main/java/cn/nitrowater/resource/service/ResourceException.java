package cn.nitrowater.resource.service;

import org.springframework.http.HttpStatus;

/** Signals a resource-request failure, carrying the HTTP status to return. */
public class ResourceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * @param status HTTP status to respond with
     * @param code stable machine-readable code (e.g. {@code unsupported_type})
     * @param message human-readable explanation
     */
    public ResourceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
