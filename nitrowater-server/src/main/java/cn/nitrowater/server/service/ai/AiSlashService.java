package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.model.AiDailyUsage;
import cn.nitrowater.server.service.ai.model.agent.AgentToolProviders;
import cn.nitrowater.server.service.ai.model.Skill;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final AgentToolProviders tools;
    private final SkillRegistry skills;
    private final WebSearchProvider webSearch;
    private final String chatModel;
    private final String embeddingModel;

    public AiSlashService(AiUsageService usage, AiProfileService profiles,
            ConversationService conversations, DocsIndexService index, AiAdminSettingsService settings,
            AgentToolProviders tools, SkillRegistry skills, WebSearchProvider webSearch,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${spring.ai.openai.embedding.model:}") String embeddingModel) {
        this.usage = usage;
        this.profiles = profiles;
        this.conversations = conversations;
        this.index = index;
        this.settings = settings;
        this.tools = tools;
        this.skills = skills;
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
    public Quota quota(long uid) {
        AiDailyUsage today = usage.forUser(uid);
        Instant resetAt = today.date().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Quota(today.dailyTokenLimit(), today.totalTokens(), today.inputTokens(),
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
    public Profile profile(long uid) {
        AiProfileService.Profile profile = profiles.get(uid);
        return new Profile(profile.exists(), profile.language(), profile.replyStyle(), profile.expertise(),
                profile.timezone(), profile.preferences(), profile.inferenceOptIn(), profile.updatedAt());
    }

    /**
     * {@code /status}: the self-check snapshot shown to users (models, index, switches, tools).
     *
     * @return current runtime state; read-only and side-effect free
     */
    public Status status() {
        DocsIndexService.IndexStatus status = index.status();
        AiAdminSettingsService.Settings runtime = settings.get();
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("ai", runtime.aiEnabled());
        features.put("chat", runtime.chatEnabled());
        features.put("agent", runtime.agentEnabled());
        features.put("docs", runtime.documentSearchEnabled());
        features.put("web", runtime.webSearchEnabled() && webSearch.enabled());
        features.put("code", runtime.codeRunnerEnabled());
        features.put("skills", runtime.skillsEnabled());
        return new Status(chatModel, embeddingModel, index.indexing(), status.indexed(), status.files(),
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
    public ConversationService.ContextReset clearContext(long uid, String conversationUuid) {
        return conversations.resetContext(conversationUuid, uid);
    }

    private List<String> enabledTools(AiAdminSettingsService.Settings runtime) {
        List<String> names = new ArrayList<>();
        for (ToolCallback callback : tools.core().getToolCallbacks()) {
            String name = callback.getToolDefinition().name();
            if (runtime.toolEnabled(name)) {
                names.add(name);
            }
        }
        boolean webReady = runtime.webSearchEnabled() && webSearch.enabled();
        for (ToolCallback callback : tools.web().getToolCallbacks()) {
            String name = callback.getToolDefinition().name();
            if (webReady && runtime.toolEnabled(name)) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private List<String> skillNames(AiAdminSettingsService.Settings runtime) {
        if (!runtime.skillsEnabled()) {
            return List.of();
        }
        return skills.list().stream().map(Skill::name).toList();
    }

    /**
     * Card payload for {@code /quota}.
     *
     * @param dailyTokenLimit configured ceiling
     * @param totalTokens used tokens ({@code input + output})
     * @param inputTokens prompt-side usage
     * @param outputTokens completion-side usage
     * @param requestCount requests admitted today
     * @param date UTC day the counters belong to
     * @param resetAt next UTC midnight
     */
    public record Quota(long dailyTokenLimit, long totalTokens, long inputTokens, long outputTokens,
            int requestCount, LocalDate date, Instant resetAt) {
    }

    /**
     * Card payload for {@code /profile}.
     *
     * @param exists whether the user has a stored row
     * @param language preferred answer language
     * @param replyStyle {@code CONCISE} / {@code BALANCED} / {@code DETAILED}
     * @param expertise {@code BEGINNER} / {@code INTERMEDIATE} / {@code EXPERT}
     * @param timezone IANA timezone, if set
     * @param preferences flattened free-form preferences
     * @param inferenceOptIn whether automatic inference is allowed (T13, default off)
     * @param updatedAt last change, or {@code null} when no row exists
     */
    public record Profile(boolean exists, String language, String replyStyle, String expertise,
            String timezone, List<String> preferences, boolean inferenceOptIn, Instant updatedAt) {
    }

    /**
     * Card payload for {@code /status}.
     *
     * @param chatModel chat model id
     * @param embeddingModel embedding model id
     * @param indexing whether a re-index is running
     * @param indexed whether the corpus is indexed
     * @param files files read by the last rebuild
     * @param chunks indexed chunks
     * @param indexedAt last rebuild time
     * @param indexError last rebuild failure, or {@code null}
     * @param features feature switches ({@code ai/chat/agent/docs/web/code/skills})
     * @param tools tool names currently advertised to the model
     * @param skills skill names currently available
     * @param dailyTokenLimit per-user daily ceiling
     */
    public record Status(String chatModel, String embeddingModel, boolean indexing, boolean indexed,
            int files, int chunks, Instant indexedAt, String indexError, Map<String, Boolean> features,
            List<String> tools, List<String> skills, long dailyTokenLimit) {
    }
}
