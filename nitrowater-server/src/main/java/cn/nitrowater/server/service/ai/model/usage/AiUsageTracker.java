package cn.nitrowater.server.service.ai.model.usage;

import cn.nitrowater.server.service.ai.ops.AiUsageService;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Per-call token accounting hook.
 *
 * <p>Lets the model-calling services stay independent of the quota implementation (and makes the
 * streaming loop unit-testable without a database): a real turn uses
 * {@link AiUsageService.Session}, tests can pass a no-op recorder.</p>
 */
public interface AiUsageTracker {

    /**
     * Records provider usage metadata found on one stream chunk or response.
     *
     * @param usage usage metadata, may be {@code null}
     */
    void record(Usage usage);

    /**
     * Appends visible answer text so a fallback estimate is possible when the provider omits usage.
     *
     * @param text generated text, may be {@code null}
     */
    void append(String text);

    /**
     * Commits what this turn has measured so far without releasing the hold.
     *
     * <p>Called at every round boundary so a long agent turn is visible to concurrent turns (and to
     * the admin dashboard) while it is still running, instead of appearing all at once when it
     * settles. The hold itself stays in place until {@link #finish()}.</p>
     */
    default void flush() {
    }

    /**
     * Settles the reservation exactly once.
     *
     * @return the provider usage or the marked fallback estimate
     */
    AiTokenUsage finish();

    /**
     * Estimates how many tokens are left in the user's daily budget, so a tool loop can stop before
     * a turn eats the rest of the day.
     *
     * <p>Deliberately approximate and dependency-free: the default is "unknown" (no limit), which
     * keeps test doubles and non-quota callers working unchanged. A real session subtracts what the
     * day had already spent when this call was admitted plus what this turn has consumed so far.</p>
     *
     * @return approximate remaining tokens, or {@link Long#MAX_VALUE} when the tracker has no quota
     */
    default long remainingTokens() {
        return Long.MAX_VALUE;
    }
}
