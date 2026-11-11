package cn.nitrowater.server.service.ai.ops;

import cn.nitrowater.server.infra.entity.AiAdminSetting;
import cn.nitrowater.server.infra.repository.AiAdminSettingRepository;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.lib.utils.StringUtil;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashSet;
import java.util.Set;

/** Persists the singleton runtime controls exposed by the AI admin console. */
@Service
public class AiAdminSettingsService {

    /** JSON shape of {@code ai_admin_setting.disabled_tools}. */
    private static final TypeReference<Set<String>> TOOL_NAMES = new TypeReference<Set<String>>() {
    };

    private final AiAdminSettingRepository repository;

    public AiAdminSettingsService(AiAdminSettingRepository repository) {
        this.repository = repository;
    }

    /** @return persisted administrative controls */
    public AiSettings get() {
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
    public AiSettings update(AiSettings settings, long adminUid) {
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

    private static AiSettings toSettings(AiAdminSetting setting) {
        return new AiSettings(setting.getDailyTokenLimit(), setting.getAiEnabled(), setting.getChatEnabled(),
                setting.getAgentEnabled(), setting.getDocumentSearchEnabled(), setting.getWebSearchEnabled(),
                setting.getCodeRunnerEnabled(), setting.getSkillsEnabled(), setting.getModerationMode(),
                setting.getGlobalDailyTokenLimit(), setting.getGlobalQuotaEnabled());
    }

    /** @return tool names currently hidden from the model */
    public Set<String> disabledTools() {
        return disabledTools(requireSetting());
    }

    /**
     * Enables or disables one tool for subsequent turns.
     *
     * @param toolName registered tool name
     * @param enabled whether the model may see it
     * @return the persisted settings snapshot
     */
    @Transactional
    public AiSettings setToolEnabled(String toolName, boolean enabled) {
        if (StringUtil.isBlank(toolName)) {
            throw new IllegalArgumentException("tool name is required");
        }
        AiAdminSetting setting = repository.findForUpdate(AiAdminSetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("AI admin settings are not initialized"));
        Set<String> disabled = new LinkedHashSet<>(disabledTools(setting));
        if (enabled) {
            disabled.remove(toolName);
        } else {
            disabled.add(toolName);
        }
        setting.setDisabledTools(JsonUtil.toJson(disabled));
        return toSettings(repository.save(setting));
    }

    private AiAdminSetting requireSetting() {
        return repository.findById(AiAdminSetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("AI admin settings are not initialized"));
    }

    private static Set<String> disabledTools(AiAdminSetting setting) {
        Set<String> parsed = JsonUtil.fromJson(setting.getDisabledTools(), TOOL_NAMES);
        return parsed == null || parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);
    }

}
