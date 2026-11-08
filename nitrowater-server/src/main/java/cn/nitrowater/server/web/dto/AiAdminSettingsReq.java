package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.service.ai.model.AiSettings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Complete administrator-editable AI settings snapshot. */
public record AiAdminSettingsReq(
        @Min(1) long dailyTokenLimit,
        @NotNull Boolean aiEnabled,
        @NotNull Boolean chatEnabled,
        @NotNull Boolean agentEnabled,
        @NotNull Boolean documentSearchEnabled,
        @NotNull Boolean webSearchEnabled,
        @NotNull Boolean codeRunnerEnabled,
        @NotNull Boolean skillsEnabled,
        /**
         * Optional so a page loaded before this field existed still saves the other switches
         * instead of failing validation; {@code null} keeps the stored posture.
         */
        ModerationMode moderationMode,
        /** Global (all users) daily token ceiling; 0 = unlimited, {@code null} keeps the stored value. */
        @PositiveOrZero Long globalDailyTokenLimit,
        /** Master switch for the global breaker; {@code null} keeps the stored value. */
        Boolean globalQuotaEnabled) {

    /** @return the validated settings value object */
    public AiSettings toSettings() {
        return new AiSettings(dailyTokenLimit, aiEnabled, chatEnabled, agentEnabled,
                documentSearchEnabled, webSearchEnabled, codeRunnerEnabled, skillsEnabled, moderationMode,
                globalDailyTokenLimit, globalQuotaEnabled);
    }
}
