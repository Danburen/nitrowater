package cn.nitrowater.server.service.ai.chat;

import cn.nitrowater.server.service.ai.conversation.ConversationService;
import cn.nitrowater.server.service.ai.memory.AiProfileService;
import cn.nitrowater.server.service.ai.model.usage.AiDailyUsage;
import cn.nitrowater.server.service.ai.model.memory.AiProfile;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.agent.ContextReset;
import cn.nitrowater.server.service.ai.model.rag.IndexStatus;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.model.agent.AgentTool;
import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.rag.DocsIndexService;
import cn.nitrowater.server.service.ai.skill.AgentSkillService;
import cn.nitrowater.server.service.ai.tool.AgentToolRegistry;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic lookups behind the chat's slash commands.
 *
 * <p>Slash commands are application shortcuts, not prompts: nothing here touches a model, so the
 * results cost zero tokens and cannot be hallucinated. The SPA calls these endpoints directly and
 * renders the payload as a system card; neither the command nor its result is ever persisted as
 * conversation content, which keeps later prompts free of system data.</p>
 *
 * <p>Natural-language equivalents ("我还能用多少额度") stay on the agent path through a tool call,
 * where the result <em>should</em> enter the context so the model can phrase an answer.</p>
 */
@Service
public class AiSlashService {

    private final AiUsageService usage;
    private final AiProfileService profiles;
    private final ConversationService conversations;
    private final DocsIndexService index;
    private final AiAdminSettingsService settings;
    private final AgentToolRegistry tools;
    private final AgentSkillService skillService;
    private final WebSearchProvider webSearch;
    private final String chatModel;
    private final String embeddingModel;

    public AiSlashService(AiUsageService usage, AiProfileService profiles,
            ConversationService conversations, DocsIndexService index, AiAdminSettingsService settings,
            AgentToolRegistry tools, AgentSkillService skillService, WebSearchProvider webSearch,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${spring.ai.openai.embedding.model:}") String embeddingModel) {
        this.usage = usage;
        this.profiles = profiles;
        this.conversations = conversations;
        this.index = index;
        this.settings = settings;
        this.tools = tools;
        this.skillService = skillService;
        this.webSearch = webSearch;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /**
     * {@code /quota}: today's UTC-day token budget.
     *
     * @param uid authenticated user id
     * @return the current counters plus the next reset instant
     */
    public AiSlashQuota quota(long uid) {
        AiDailyUsage today = usage.forUser(uid);
        Instant resetAt = today.date().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new AiSlashQuota(today.dailyTokenLimit(), today.totalTokens(), today.inputTokens(),
                today.outputTokens(), today.requestCount(), today.date(), resetAt);
    }

    /**
     * {@code /profile}: the stored preferences the assistant would apply.
     *
     * <p>Only explicit columns are read; automatic inference (T13) is not implemented yet, so an
     * existing row with default values is still reported as {@code exists=true} together with
     * {@code inferenceOptIn=false}.</p>
     *
     * @param uid authenticated user id
     * @return the profile, or a defaults-only snapshot when the user has no row yet
     */
    public AiProfile profile(long uid) {
        return profiles.get(uid);
    }

    /**
     * {@code /status}: the self-check snapshot shown to users (models, index, switches, tools).
     *
     * @return current runtime state; read-only and side-effect free
     */
    public AiSlashStatus status() {
        IndexStatus status = index.status();
        AiSettings runtime = settings.get();
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("ai", runtime.aiEnabled());
        features.put("chat", runtime.chatEnabled());
        features.put("agent", runtime.agentEnabled());
        features.put("docs", runtime.documentSearchEnabled());
        features.put("web", runtime.webSearchEnabled() && webSearch.enabled());
        features.put("code", runtime.codeRunnerEnabled());
        features.put("skills", runtime.skillsEnabled());
        return new AiSlashStatus(chatModel, embeddingModel, index.indexing(), status.indexed(), status.files(),
                status.chunks(), status.indexedAt(), status.error(), features, enabledTools(runtime),
                skillNames(runtime), runtime.dailyTokenLimit());
    }

    /**
     * {@code /clear}: drops the stored turns from the model context, keeping the transcript.
     *
     * @param uid authenticated user id
     * @param conversationUuid conversation to reset (required)
     * @return the reset outcome
     */
    public ContextReset clearContext(long uid, String conversationUuid) {
        return conversations.resetContext(conversationUuid, uid);
    }

    private List<String> enabledTools(AiSettings runtime) {
        // Consent is granted: this card answers "what could the model use", while availability and
        // the administrator's switches are still enforced inside the registry.
        return tools.forTurn(runtime, settings.disabledTools(), true).stream()
                .map(AgentTool::name)
                .toList();
    }

    private List<String> skillNames(AiSettings runtime) {
        return skillService.catalog(runtime).stream().map(Skill::name).toList();
    }
}
