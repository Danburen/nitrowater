package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;

/**
 * Administrator-visible AI settings snapshot.
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
public record AiSettingsResp(
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

    /**
     * @param settings service settings
     * @return the wire view
     */
    public static AiSettingsResp from(AiSettings settings) {
        return new AiSettingsResp(settings.dailyTokenLimit(), settings.aiEnabled(),
                settings.chatEnabled(), settings.agentEnabled(), settings.documentSearchEnabled(),
                settings.webSearchEnabled(), settings.codeRunnerEnabled(), settings.skillsEnabled(),
                settings.moderationMode(), settings.globalDailyTokenLimit(),
                settings.globalQuotaEnabled());
    }
}
