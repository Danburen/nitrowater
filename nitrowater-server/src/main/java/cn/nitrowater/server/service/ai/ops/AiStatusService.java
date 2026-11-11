package cn.nitrowater.server.service.ai.ops;

import cn.nitrowater.server.service.ai.model.usage.AiGlobalQuota;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.admin.AiStatusSnapshot;
import cn.nitrowater.server.service.ai.model.rag.IndexStatus;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.rag.DocsIndexService;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Runtime status read model for NitroBot.
 *
 * <p>Combines the documentation index, the configured models and the administrator's runtime
 * switches into the single snapshot the status endpoint renders.</p>
 */
@Service
public class AiStatusService {

    private final DocsIndexService indexService;
    private final AiProperties properties;
    private final WebSearchProvider webSearchProvider;
    private final AiAdminSettingsService adminSettings;
    private final AiUsageService usage;
    private final String chatModel;
    private final String embeddingModel;

    public AiStatusService(DocsIndexService indexService, AiProperties properties,
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

    /**
     * @return whether the corpus is indexed and which models / switches are active
     */
    public AiStatusSnapshot status() {
        IndexStatus index = indexService.status();
        AiSettings runtime = adminSettings.get();
        return new AiStatusSnapshot(
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
                AiGlobalQuota.from(usage.globalToday()));
    }
}
