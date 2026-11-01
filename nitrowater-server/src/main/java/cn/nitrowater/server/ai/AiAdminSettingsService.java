package cn.nitrowater.server.ai;

import cn.nitrowater.server.infra.entity.AiAdminSetting;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.repository.AiAdminSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the singleton runtime controls exposed by the AI admin console. */
@Service
public class AiAdminSettingsService {

    private final AiAdminSettingRepository repository;

    public AiAdminSettingsService(AiAdminSettingRepository repository) {
        this.repository = repository;
    }

    /** @return persisted administrative controls */
    public Settings get() {
        return toSettings(repository.findById(AiAdminSetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("AI admin settings are not initialized")));
    }

    /** Loads and locks the singleton for short quota transactions. */
    @Transactional
    public AiAdminSetting lock() {
        return repository.findForUpdate(AiAdminSetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("AI admin settings are not initialized"));
    }

    /** Saves the administrator's complete settings snapshot. */
    @Transactional
    public Settings update(Settings settings, long adminUid) {
        AiAdminSetting setting = repository.findForUpdate(AiAdminSetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("AI admin settings are not initialized"));
        setting.setDailyTokenLimit(settings.dailyTokenLimit());
        setting.setAiEnabled(settings.aiEnabled());
        setting.setChatEnabled(settings.chatEnabled());
        setting.setAgentEnabled(settings.agentEnabled());
        setting.setDocumentSearchEnabled(settings.documentSearchEnabled());
        setting.setWebSearchEnabled(settings.webSearchEnabled());
        setting.setCodeRunnerEnabled(settings.codeRunnerEnabled());
        setting.setSkillsEnabled(settings.skillsEnabled());
        // A client that predates the moderation switch sends null; keep the stored posture rather
        // than resetting it to a hard-coded default.
        if (settings.moderationMode() != null) {
            setting.setModerationMode(settings.moderationMode());
        }
        // Same rule for the global-quota fields: absent means "leave as configured".
        if (settings.globalDailyTokenLimit() != null) {
            setting.setGlobalDailyTokenLimit(Math.max(0, settings.globalDailyTokenLimit()));
        }
        if (settings.globalQuotaEnabled() != null) {
            setting.setGlobalQuotaEnabled(settings.globalQuotaEnabled());
        }
        setting.setUpdatedByUid(adminUid);
        return toSettings(repository.save(setting));
    }

    private static Settings toSettings(AiAdminSetting setting) {
        return new Settings(setting.getDailyTokenLimit(), setting.getAiEnabled(), setting.getChatEnabled(),
                setting.getAgentEnabled(), setting.getDocumentSearchEnabled(), setting.getWebSearchEnabled(),
                setting.getCodeRunnerEnabled(), setting.getSkillsEnabled(), setting.getModerationMode(),
                setting.getGlobalDailyTokenLimit(), setting.getGlobalQuotaEnabled());
    }

    /**
     * Persisted model capabilities, the per-user daily ceiling and the global breaker.
     *
     * <p>The two global fields are nullable so a client that predates them can still save the rest
     * of the snapshot without silently zeroing the configured budget.</p>
     */
    public record Settings(
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
        public Settings(long dailyTokenLimit, boolean aiEnabled, boolean chatEnabled, boolean agentEnabled,
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

        /**
         * Single source of truth for tool gating; acknowledged by both the agent request builder and
         * the admin catalog so the console never shows a state the runtime ignores.
         *
         * @param toolName registered tool name
         * @return whether the tool may be advertised for this turn
         */
        public boolean toolEnabled(String toolName) {
            if (toolName == null || toolName.isBlank()) {
                return false;
            }
            return switch (toolName) {
                case "searchDocs" -> documentSearchEnabled;
                case "loadSkill" -> skillsEnabled;
                case "runCode" -> codeRunnerEnabled;
                case "webSearch" -> webSearchEnabled;
                // Core tools are code-registered; a tool without an admin switch stays available.
                default -> true;
            };
        }
    }
}
