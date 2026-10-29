package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.ai.AiAdminSettingsService.Settings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Complete administrator-editable AI settings snapshot. */
public record AiAdminSettingsReq(
        @Min(1) long dailyTokenLimit,
        @NotNull Boolean aiEnabled,
        @NotNull Boolean chatEnabled,
        @NotNull Boolean agentEnabled,
        @NotNull Boolean documentSearchEnabled,
        @NotNull Boolean webSearchEnabled,
        @NotNull Boolean codeRunnerEnabled,
        @NotNull Boolean skillsEnabled) {

    /** @return the validated settings value object */
    public Settings toSettings() {
        return new Settings(dailyTokenLimit, aiEnabled, chatEnabled, agentEnabled,
                documentSearchEnabled, webSearchEnabled, codeRunnerEnabled, skillsEnabled);
    }
}
