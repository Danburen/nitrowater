package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.AiUsageService;
import cn.nitrowater.server.service.ai.DocsIndexService;
import cn.nitrowater.server.service.ai.model.AiDailyUsage;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import cn.nitrowater.server.web.dto.AiStatusResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * NitroBot informational endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/ai/status} — index/model status and runtime switches (login required);</li>
 *   <li>{@code GET /api/ai/usage} — the caller's UTC-day token usage.</li>
 * </ul>
 *
 * <p>Chat turns only go through {@link AiAgentController}: there is no retrieval-only route, so a
 * question the model answers without calling any tool is simply a single-round agent turn.</p>
 */
@RestController
public class AiController {

    private final DocsIndexService indexService;
    private final AiProperties properties;
    private final WebSearchProvider webSearchProvider;
    private final AiAdminSettingsService adminSettings;
    private final AiUsageService usage;
    private final String chatModel;
    private final String embeddingModel;

    public AiController(DocsIndexService indexService, AiProperties properties,
            WebSearchProvider webSearchProvider, AiAdminSettingsService adminSettings,
            AiUsageService usage,
            @Value("${spring.ai.openai.chat.model:}") String chatModel,
            @Value("${spring.ai.openai.embedding.model:}") String embeddingModel) {
        this.indexService = indexService;
        this.properties = properties;
        this.webSearchProvider = webSearchProvider;
        this.adminSettings = adminSettings;
        this.usage = usage;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /** Login: report whether the corpus is indexed and which models are configured. */
    @GetMapping("/api/ai/status")
    public AiStatusResp status() {
        DocsIndexService.IndexStatus index = indexService.status();
        AiAdminSettingsService.Settings runtime = adminSettings.get();
        return new AiStatusResp(
                properties.enabled() && runtime.aiEnabled(),
                indexService.indexing(),
                index.indexed(),
                index.files(),
                index.chunks(),
                index.indexedAt(),
                index.durationMs(),
                index.error(),
                indexService.collectionName(),
                chatModel,
                embeddingModel,
                properties.topK(),
                properties.similarityThreshold(),
                runtime.webSearchEnabled() && webSearchProvider.enabled(),
                runtime.dailyTokenLimit(), runtime.chatEnabled(), runtime.agentEnabled(),
                runtime.documentSearchEnabled(), runtime.codeRunnerEnabled(), runtime.skillsEnabled(),
                AiStatusResp.GlobalQuota.from(usage.globalToday()));
    }

    /** Login: return the current user's UTC-day token usage. */
    @GetMapping("/api/ai/usage")
    public AiDailyUsage currentUsage(Principal principal) {
        return usage.forUser(CurrentUser.requireUid(principal));
    }
}
