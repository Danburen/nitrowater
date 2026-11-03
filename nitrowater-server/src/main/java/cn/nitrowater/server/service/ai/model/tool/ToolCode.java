package cn.nitrowater.server.service.ai.model.tool;

import cn.nitrowater.lib.api.ResponseCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Outcome codes shared by every agent tool.
 *
 * <p>{@link ToolResponse} derives its {@code success} and {@code retryable} flags from these codes,
 * so a tool cannot answer "ok" while the UI marks the call as failed: one code drives the model
 * payload, the trace entry and the step card.</p>
 *
 * <p>{@code success} means <em>the call was handled</em>, not that the user's goal was reached. A
 * snippet that fails to compile is a successful {@code runCode} call ({@link #COMPILE_ERROR}) whose
 * {@code data} carries {@code stderr}; marking it failed would hide the diagnostics from the model
 * and look like a broken sandbox. Codes are appended only, never reused: the system prompt and the
 * UI key off them.</p>
 */
@Getter
public enum ToolCode implements ResponseCode {

    /** The tool did its job and produced a payload. */
    OK("tool.ok", true, null),
    /** A memory row was created. */
    SAVED("tool.saved", true, null),
    /** An existing memory row was refreshed instead of adding a duplicate. */
    UPDATED("tool.updated", true, null),
    /** Execution is possible but the user must confirm; retrying with the same input is expected. */
    QUEUED("tool.queued", true, false),
    /** The snippet did not compile; the model can fix it from {@code data.stderr}. */
    COMPILE_ERROR("tool.compile_error", true, true),
    /** The snippet exceeded its time limit. */
    RUN_TIMEOUT("tool.run_timeout", true, true),
    /** The snippet crashed at run time. */
    RUNTIME_ERROR("tool.runtime_error", true, true),

    /** This turn has no authenticated user. */
    UNAUTHORIZED("tool.unauthorized", false, false),
    /** The arguments are unusable (unknown skill, unknown memory kind, empty query...). */
    INVALID_REQUEST("tool.invalid_request", false, false),
    /** The capability is off on this server. */
    DISABLED("tool.disabled", false, false),
    /** The same request already produced results in this turn. */
    DUPLICATE("tool.duplicate", false, false),
    /** The per-turn budget is spent. */
    LIMIT_REACHED("tool.limit_reached", false, false),
    /** The per-conversation attempt budget is spent. */
    RETRY_EXHAUSTED("tool.retry_exhausted", false, false),
    /** A domain rule (or the moderation guard) refused the write. */
    REJECTED("tool.rejected", false, false),
    /** The concurrency queue is saturated. */
    QUEUE_FULL("tool.queue_full", false, true),
    /** Waiting in the queue timed out (or the user's own slot is busy). */
    QUEUE_TIMEOUT("tool.queue_timeout", false, true),
    /** Per-minute execution quota is exhausted. */
    RATE_LIMITED("tool.rate_limited", false, true),
    /** Per-day execution quota is exhausted; waiting will not help. */
    DAILY_QUOTA("tool.daily_quota", false, false),
    /** A downstream dependency failed; a later retry may succeed. */
    UNAVAILABLE("tool.unavailable", false, true);

    /**
     * Statuses written before this enum existed. Only needed to read back tool traces persisted by
     * older builds; delete once no stored conversation predates the {@link ToolResponse} change.
     */
    private static final Set<String> LEGACY_FAILURES = Set.of(
            "failed", "fallback_failed", "disabled", "duplicate", "limit_reached",
            "unauthorized", "invalid_request", "retry_exhausted", "queue_full", "queue_timeout",
            "rate_limited", "daily_quota", "server_unavailable");

    private static final Map<String, ToolCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ToolCode::getCode, Function.identity()));

    private final String code;
    private final boolean success;
    private final Boolean retryable;

    ToolCode(String code, boolean success, Boolean retryable) {
        this.code = code;
        this.success = success;
        this.retryable = retryable;
    }

    /** @return the JSON code (e.g. {@code tool.compile_error}) */
    @Override
    public String getCode() {
        return code;
    }

    /** Tool codes have no argument variants, so normalisation is the identity. */
    @Override
    public ResponseCode toNoArgsResponse() {
        return this;
    }

    /**
     * @param status a code from {@link #getCode()}, or a {@code null}/legacy trace status
     * @return whether that status counts as success for the UI badge
     */
    public static boolean isSuccessCode(String status) {
        if (status == null) {
            return true;
        }
        ToolCode code = BY_CODE.get(status);
        return code == null ? !LEGACY_FAILURES.contains(status) : code.success;
    }
}
