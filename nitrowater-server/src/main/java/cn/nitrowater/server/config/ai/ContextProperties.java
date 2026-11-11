package cn.nitrowater.server.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Context-window and compaction settings ({@code app.ai.context.*}).
 *
 * <p>The numbers are expressed against the model's real context window, which is how agent
 * frameworks normally size them: {@code model-window-tokens} is the window the provider bills for
 * (1M for the {@code deepseek-chat} alias), {@code history-max-tokens} (or, when it is absent, the
 * {@code history-window-ratio} share of the window) is the replay budget, and
 * {@code compact-trigger-ratio} is where compaction starts. The budget does not scale with the
 * window: every round resends it, so it is pinned to what a day of quota can afford.</p>
 *
 * <p>Two knobs decide what the model sees in one turn:</p>
 * <ul>
 *   <li>{@link #historyBudgetTokens()} — the primary budget. History is filled newest-first until
 *       the budget is exhausted, so a long answer cannot crowd out the last few turns;</li>
 *   <li>{@code history-hard-max-messages} — a hard ceiling on the rows read per turn, keeping the
 *       query bounded no matter how large the budget is.</li>
 * </ul>
 *
 * <p>Compaction folds turns that would leave the window into {@code ai_conversation.summary}. It
 * runs after a turn (never on the request path) and once either trigger fires: the unsummarized
 * backlog reached {@code compact-trigger-messages}, or it reached
 * {@link #compactTriggerTokens()} estimated tokens. The token trigger is the primary one — it speaks
 * the same unit as the replay budget, and it fires <em>below</em> the budget on purpose: folding
 * before the window starts dropping turns is what keeps the summary contiguous with what the model
 * can still see. A ratio above 1 would reintroduce the gap, so it is clamped.</p>
 *
 * @param modelWindowTokens context window of the chat model, in tokens ({@code 1000000} for the
 *        {@code deepseek-chat} alias, which is served by {@code deepseek-flash})
 * @param historyWindowRatio share of {@code model-window-tokens} the replayed history may spend,
 *        used only when {@code history-max-tokens} is absent; {@code 0} falls back to the legacy
 *        turn count
 * @param historyMaxTokens explicit replay budget in tokens ({@code null} = derive from the ratio);
 *        {@code 0} forces the legacy {@code max-history-turns} mode. Production pins this to 64K:
 *        the budget is a cost lever, so it does not grow with the model window
 * @param historyHardMaxMessages rows read per turn (hard ceiling)
 * @param compactEnabled master switch for automatic and manual compaction
 * @param compactTriggerMessages unsummarized messages that trigger an automatic compaction
 * @param compactTriggerRatio share of the replay budget at which the token trigger fires
 *        ({@code 0} disables the token trigger and leaves the message count in charge)
 * @param compactKeepTurns turns kept verbatim at the head of the window after a compaction
 * @param summaryMaxChars maximum stored summary length
 * @param summaryInputMaxChars maximum transcript size sent to the summarizer in one pass
 */
@ConfigurationProperties(prefix = "app.ai.context")
public record ContextProperties(
        int modelWindowTokens,
        Double historyWindowRatio,
        Integer historyMaxTokens,
        int historyHardMaxMessages,
        boolean compactEnabled,
        int compactTriggerMessages,
        Double compactTriggerRatio,
        int compactKeepTurns,
        int summaryMaxChars,
        int summaryInputMaxChars
) {

    private static final int DEFAULT_MODEL_WINDOW_TOKENS = 1_000_000;
    private static final double DEFAULT_HISTORY_WINDOW_RATIO = 0.064;
    private static final double DEFAULT_TRIGGER_RATIO = 0.8;
    private static final int DEFAULT_HARD_MAX_MESSAGES = 120;
    private static final int DEFAULT_TRIGGER_MESSAGES = 32;
    private static final int DEFAULT_SUMMARY_MAX_CHARS = 2000;
    private static final int DEFAULT_SUMMARY_INPUT_MAX_CHARS = 24000;

    public ContextProperties {
        modelWindowTokens = modelWindowTokens > 0 ? modelWindowTokens : DEFAULT_MODEL_WINDOW_TOKENS;
        historyMaxTokens = historyMaxTokens == null ? null : Math.max(0, historyMaxTokens);
        historyHardMaxMessages = historyHardMaxMessages > 0
                ? historyHardMaxMessages : DEFAULT_HARD_MAX_MESSAGES;
        compactTriggerMessages = compactTriggerMessages > 0
                ? compactTriggerMessages : DEFAULT_TRIGGER_MESSAGES;
        compactKeepTurns = Math.max(0, compactKeepTurns);
        summaryMaxChars = summaryMaxChars > 0 ? summaryMaxChars : DEFAULT_SUMMARY_MAX_CHARS;
        summaryInputMaxChars = summaryInputMaxChars > 0
                ? summaryInputMaxChars : DEFAULT_SUMMARY_INPUT_MAX_CHARS;
    }

    /**
     * @return prompt budget for the replayed history, in tokens; {@code 0} when the legacy
     *         turn-count rule is in charge
     */
    public int historyBudgetTokens() {
        if (historyMaxTokens != null) {
            // An explicit budget never exceeds the window it is a share of: a typo (640000 on a 64K
            // model) would otherwise build prompts the provider rejects outright.
            return Math.min(historyMaxTokens, modelWindowTokens);
        }
        return (int) Math.round(modelWindowTokens * ratio(historyWindowRatio,
                DEFAULT_HISTORY_WINDOW_RATIO));
    }

    /** @return whether history is trimmed by a token budget instead of a turn count */
    public boolean tokenBudgetEnabled() {
        return historyBudgetTokens() > 0;
    }

    /** @return messages kept verbatim at the head of the window after a compaction */
    public int keepMessages() {
        return compactKeepTurns * 2;
    }

    /**
     * @return unsummarized backlog size, in estimated tokens, that triggers an automatic
     *         compaction; {@code 0} when the token trigger is disabled
     */
    public int compactTriggerTokens() {
        int budget = historyBudgetTokens();
        double configured = ratio(compactTriggerRatio, DEFAULT_TRIGGER_RATIO);
        if (budget <= 0 || configured <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(budget * configured));
    }

    /** @return whether an automatic compaction may be triggered by backlog size in tokens */
    public boolean tokenTriggerEnabled() {
        return compactTriggerTokens() > 0;
    }

    /**
     * @param value configured ratio; {@code null} when the property is absent from YAML
     * @param fallback value used when the property is absent
     * @return the ratio, clamped to {@code [0, 1]}; {@code 0} stays {@code 0} (a deliberate "off")
     */
    private static double ratio(Double value, double fallback) {
        double resolved = value == null ? fallback : value;
        return Math.max(0.0, Math.min(1.0, resolved));
    }
}
