package cn.nitrowater.server.service.ai.model.admin;

import cn.nitrowater.server.infra.enums.ModerationMode;

/**
 * Persisted AI runtime switches and quota settings.
 *
 * @param dailyTokenLimit per-user daily token limit
 * @param aiEnabled master switch
 * @param chatEnabled whether chat mode is enabled
 * @param agentEnabled whether agent mode is enabled
 * @param documentSearchEnabled whether local documentation retrieval is enabled
 * @param webSearchEnabled whether the web-search tool is enabled
 * @param codeRunnerEnabled whether the code runner is enabled
 * @param skillsEnabled whether skills are available
 * @param moderationMode active moderation posture
 * @param globalDailyTokenLimit platform-wide ceiling, {@code null} when unset
 * @param globalQuotaEnabled whether the platform breaker is switched on
 */
public record AiSettings(
        long dailyTokenLimit,
        boolean aiEnabled,
        boolean chatEnabled,
        boolean agentEnabled,
        boolean documentSearchEnabled,
        boolean webSearchEnabled,
        boolean codeRunnerEnabled,
        boolean skillsEnabled,
        ModerationMode moderationMode,
        Long globalDailyTokenLimit,
        Boolean globalQuotaEnabled) {

    /** Compatibility constructor for callers (mostly tests) that predate the global breaker. */
    public AiSettings(long dailyTokenLimit, boolean aiEnabled, boolean chatEnabled, boolean agentEnabled,
            boolean documentSearchEnabled, boolean webSearchEnabled, boolean codeRunnerEnabled,
            boolean skillsEnabled, ModerationMode moderationMode) {
        this(dailyTokenLimit, aiEnabled, chatEnabled, agentEnabled, documentSearchEnabled,
                webSearchEnabled, codeRunnerEnabled, skillsEnabled, moderationMode, null, null);
    }

    /** @return the configured global ceiling, or {@code 0} when unlimited/unset */
    public long globalLimit() {
        return globalDailyTokenLimit == null ? 0L : globalDailyTokenLimit;
    }

    /** @return whether the global breaker is switched on AND has a positive ceiling */
    public boolean globalQuotaActive() {
        return Boolean.TRUE.equals(globalQuotaEnabled) && globalLimit() > 0;
    }
}
