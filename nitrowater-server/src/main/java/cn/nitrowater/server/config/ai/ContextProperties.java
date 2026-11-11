package cn.nitrowater.server.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Context-window and compaction settings ({@code app.ai.context.*}).
 *
 * <p>Two knobs decide what the model sees in one turn:</p>
 * <ul>
 *   <li>{@code history-max-tokens} — the primary budget. History is filled newest-first until the
 *       budget is exhausted, so a long answer cannot crowd out the last few turns;</li>
 *   <li>{@code history-hard-max-messages} — a hard ceiling on the rows read per turn, keeping the
 *       query bounded no matter how large the budget is.</li>
 * </ul>
 *
 * <p>When {@code history-max-tokens} is {@code 0} the old turn-count rule
 * ({@code app.ai.max-history-turns}) takes over, which is also the legacy client-history limit.</p>
 *
 * <p>Compaction folds turns that would leave the window into {@code ai_conversation.summary}. It
 * runs after a turn (never on the request path) and only once the pending messages reach
 * {@code compact-trigger-messages}; a smaller value means fresher summaries and more model calls.</p>
 *
 * @param historyMaxTokens prompt budget for the replayed history; {@code 0} disables it
 * @param historyHardMaxMessages rows read per turn (hard ceiling)
 * @param compactEnabled master switch for automatic and manual compaction
 * @param compactTriggerMessages unsummarized messages that trigger an automatic compaction
 * @param compactKeepTurns turns kept verbatim at the head of the window after a compaction
 * @param summaryMaxChars maximum stored summary length
 * @param summaryInputMaxChars maximum transcript size sent to the summarizer in one pass
 */
@ConfigurationProperties(prefix = "app.ai.context")
public record ContextProperties(
        int historyMaxTokens,
        int historyHardMaxMessages,
        boolean compactEnabled,
        int compactTriggerMessages,
        int compactKeepTurns,
        int summaryMaxChars,
        int summaryInputMaxChars
) {

    private static final int DEFAULT_HARD_MAX_MESSAGES = 60;
    private static final int DEFAULT_TRIGGER_MESSAGES = 32;
    private static final int DEFAULT_SUMMARY_MAX_CHARS = 800;
    private static final int DEFAULT_SUMMARY_INPUT_MAX_CHARS = 8000;

    public ContextProperties {
        historyMaxTokens = Math.max(0, historyMaxTokens);
        historyHardMaxMessages = historyHardMaxMessages > 0
                ? historyHardMaxMessages : DEFAULT_HARD_MAX_MESSAGES;
        compactTriggerMessages = compactTriggerMessages > 0
                ? compactTriggerMessages : DEFAULT_TRIGGER_MESSAGES;
        compactKeepTurns = Math.max(0, compactKeepTurns);
        summaryMaxChars = summaryMaxChars > 0 ? summaryMaxChars : DEFAULT_SUMMARY_MAX_CHARS;
        summaryInputMaxChars = summaryInputMaxChars > 0
                ? summaryInputMaxChars : DEFAULT_SUMMARY_INPUT_MAX_CHARS;
    }

    /** @return whether history is trimmed by a token budget instead of a turn count */
    public boolean tokenBudgetEnabled() {
        return historyMaxTokens > 0;
    }

    /** @return messages kept verbatim at the head of the window after a compaction */
    public int keepMessages() {
        return compactKeepTurns * 2;
    }
}
