package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when one agent turn outlives its wall-clock budget.
 *
 * <p>The budget covers model rounds and tool calls together, so a turn cannot creep past the SSE
 * timeout with a long chain of slow tools. Tools that hit their own deadline are handled separately
 * (they answer with a retryable {@code tool.timeout}); this exception only fires when the whole turn
 * has no time left, which is why it aborts instead of asking the model to try again.</p>
 */
public class AiTurnTimeoutException extends CodedException {

    /** Stable code shared by the JSON error body and the SSE error payload. */
    public static final String CODE = "turn_timeout";

    private final long timeoutMs;

    /**
     * @param timeoutMs the budget that was exhausted
     */
    public AiTurnTimeoutException(long timeoutMs) {
        super(HttpStatus.GATEWAY_TIMEOUT, CODE,
                "本回合已超过 " + describe(timeoutMs) + "上限，请缩短问题或稍后重试");
        this.timeoutMs = timeoutMs;
    }

    /** @return the turn budget in milliseconds */
    public long timeoutMs() {
        return timeoutMs;
    }

    private static String describe(long timeoutMs) {
        return timeoutMs >= 1000 && timeoutMs % 1000 == 0
                ? (timeoutMs / 1000) + " 秒"
                : timeoutMs + " 毫秒";
    }
}
