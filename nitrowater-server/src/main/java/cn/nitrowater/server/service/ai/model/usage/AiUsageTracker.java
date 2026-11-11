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
     * Settles the reservation exactly once.
     *
     * @return the provider usage or the marked fallback estimate
     */
    AiTokenUsage finish();
}
