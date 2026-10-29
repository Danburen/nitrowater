package cn.nitrowater.server.ai;

import cn.nitrowater.server.infra.entity.AiAdminSetting;
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
        setting.setUpdatedByUid(adminUid);
        return toSettings(repository.save(setting));
    }

    private static Settings toSettings(AiAdminSetting setting) {
        return new Settings(setting.getDailyTokenLimit(), setting.getAiEnabled(), setting.getChatEnabled(),
                setting.getAgentEnabled(), setting.getDocumentSearchEnabled(), setting.getWebSearchEnabled(),
                setting.getCodeRunnerEnabled(), setting.getSkillsEnabled());
    }

    /** Persisted model capabilities and the shared daily token ceiling. */
    public record Settings(
            long dailyTokenLimit,
            boolean aiEnabled,
            boolean chatEnabled,
            boolean agentEnabled,
            boolean documentSearchEnabled,
            boolean webSearchEnabled,
            boolean codeRunnerEnabled,
            boolean skillsEnabled) {

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
