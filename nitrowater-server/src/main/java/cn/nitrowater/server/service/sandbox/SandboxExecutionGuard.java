package cn.nitrowater.server.service.sandbox;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.ratelimit.RedisRateLimiter;
import cn.nitrowater.server.infra.ratelimit.SandboxGate;
import cn.nitrowater.server.service.sandbox.model.RunCommand;
import cn.nitrowater.server.service.sandbox.model.RunResult;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Shared admission and execution path for server-side code runs.
 *
 * <p>The HTTP sandbox endpoint and the agent's {@code runCode} tool must consume the same
 * per-user quotas and the same global queue. Keeping that policy in one component prevents the
 * agent from becoming a quota bypass. Admission is deliberately split from execution: callers can
 * inspect {@link #snapshot()} first (the agent asks the user when the queue is long), then call
 * {@link #execute(String, RunCommand)} once they are ready to wait.</p>
 */
@Component
public class SandboxExecutionGuard {

    private static final String QUOTA_KEY_PREFIX = "nitrowater:sandbox:quota:";
    private static final Duration DAILY_TTL = Duration.ofDays(2);

    private final RedisRateLimiter limiter;
    private final SandboxGate gate;
    private final SandboxProperties properties;
    private final SandboxService sandboxService;

    public SandboxExecutionGuard(RedisRateLimiter limiter, SandboxGate gate,
            SandboxProperties properties, SandboxService sandboxService) {
        this.limiter = limiter;
        this.gate = gate;
        this.properties = properties;
        this.sandboxService = sandboxService;
    }

    /**
     * Checks the user's minute/day quotas and acquires a run slot.
     *
     * @param uid the authenticated user id
     * @return a lease that must be closed when the run finishes
     * @throws GuardException when the user is unknown, over quota, or the queue cannot admit them
     */
    public SandboxGate.Lease admit(String uid) {
        return admit(uid, properties.queueWaitMs());
    }

    /**
     * Checks the user's quotas and acquires a run slot with an explicit wait budget.
     *
     * @param uid the authenticated user id
     * @param waitMs maximum time to wait for a slot
     * @return a lease that must be closed when the run finishes
     * @throws GuardException when the user is unknown, over quota, or the queue cannot admit them
     */
    public SandboxGate.Lease admit(String uid, long waitMs) {
        String user = requireUser(uid);
        if (!limiter.allowSliding(QUOTA_KEY_PREFIX + user + ":m", properties.perUserPerMinute())) {
            throw new GuardException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Too many runs, please slow down");
        }
        String dayKey = QUOTA_KEY_PREFIX + user + ":d:" + LocalDate.now(ZoneOffset.UTC);
        if (!limiter.allowFixed(dayKey, properties.perUserPerDay(), DAILY_TTL)) {
            throw new GuardException(HttpStatus.TOO_MANY_REQUESTS, "daily_quota",
                    "Daily run quota exceeded, please try again tomorrow");
        }

        try {
            return gate.acquire(user, waitMs);
        } catch (SandboxGate.GateBusyException ex) {
            throw new GuardException(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GuardException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted",
                    "Request was interrupted, please retry");
        }
    }

    /**
     * Admits one run, executes it, and always releases the slot.
     *
     * @param uid the authenticated user id
     * @param command the validated run command
     * @return the normalised sandbox result
     * @throws GuardException when admission fails
     * @throws cn.nitrowater.server.infra.exception.SandboxException when execution fails
     */
    public RunResult execute(String uid, RunCommand command) {
        return execute(uid, command, properties.queueWaitMs());
    }

    /**
     * Admits one run with an explicit wait budget, executes it, and always releases the slot.
     *
     * @param uid the authenticated user id
     * @param command the validated run command
     * @param waitMs maximum time to wait for a slot
     * @return the normalised sandbox result
     * @throws GuardException when admission fails
     * @throws cn.nitrowater.server.infra.exception.SandboxException when execution fails
     */
    public RunResult execute(String uid, RunCommand command, long waitMs) {
        try (SandboxGate.Lease ignored = admit(uid, waitMs)) {
            return sandboxService.run(command);
        }
    }

    /**
     * @return the current queue and slot pressure, used by the agent before blocking
     */
    public SandboxGate.Snapshot snapshot() {
        return gate.snapshot();
    }

    private static String requireUser(String uid) {
        if (StringUtil.isBlank(uid)) {
            throw new GuardException(HttpStatus.UNAUTHORIZED, "unauthorized",
                    "Login is required to run code");
        }
        return uid;
    }

    /** Raised when admission cannot be granted; carries the HTTP status the endpoint should use. */
    @Getter
    public static final class GuardException extends RuntimeException {

        private final HttpStatus status;
        private final String code;

        public GuardException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
