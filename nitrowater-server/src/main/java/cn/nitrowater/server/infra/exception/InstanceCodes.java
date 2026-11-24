package cn.nitrowater.server.infra.exception;

/**
 * Stable machine-readable codes for the instance / remote-monitoring API.
 *
 * <p>Codes are append-only and mirrored by the web client's {@code ResponseCode}. HTTP status only
 * serves infrastructure (retry / reauth); business branches switch on these strings.</p>
 */
public final class InstanceCodes {

    public static final String BAD_PROTOCOL = "instance_bad_protocol";
    public static final String UNAUTHORIZED = "instance_unauthorized";
    public static final String FORBIDDEN = "instance_forbidden";
    public static final String LIMIT_EXCEEDED = "instance_limit_exceeded";
    public static final String CONFLICT = "instance_conflict";
    public static final String RATE_LIMITED = "instance_rate_limited";
    public static final String OFFLINE = "instance_offline";

    private InstanceCodes() {
    }
}
