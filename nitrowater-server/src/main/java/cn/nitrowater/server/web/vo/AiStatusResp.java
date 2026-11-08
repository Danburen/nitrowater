package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiGlobalQuota;
import cn.nitrowater.server.service.ai.model.AiStatusSnapshot;

import java.time.Instant;

/**
 * Documentation-assistant status, surfaced by the chat page (and handy for smoke tests).
 *
 * @param enabled master switch
 * @param indexing whether a rebuild is running right now
 * @param indexed whether the corpus currently has points
 * @param files number of markdown files read by the last rebuild
 * @param chunks number of indexed chunks
 * @param indexedAt when the last rebuild finished ({@code null} after a restart)
 * @param durationMs duration of the last rebuild
 * @param error failure message of the last rebuild ({@code null} on success)
 * @param collection vector collection name
 * @param chatModel chat model id
 * @param embeddingModel embedding model id
 * @param topK configured retrieval size
 * @param similarityThreshold configured similarity threshold
 * @param webSearchEnabled whether the optional web-search tool is available
 * @param dailyTokenLimit per-user daily token limit
 * @param chatEnabled whether RAG/general chat mode is enabled
 * @param agentEnabled whether agent mode is enabled
 * @param documentSearchEnabled whether local documentation retrieval is enabled
 * @param codeRunnerEnabled whether the Agent code runner is enabled
 * @param skillsEnabled whether skills are available to the Agent
 * @param globalQuota platform-wide daily budget snapshot (the breaker closes chat, nothing else)
 */
public record AiStatusResp(
        boolean enabled,
        boolean indexing,
        boolean indexed,
        int files,
        int chunks,
        Instant indexedAt,
        long durationMs,
        String error,
        String collection,
        String chatModel,
        String embeddingModel,
        int topK,
        double similarityThreshold,
        boolean webSearchEnabled,
        long dailyTokenLimit,
        boolean chatEnabled,
        boolean agentEnabled,
        boolean documentSearchEnabled,
        boolean codeRunnerEnabled,
        boolean skillsEnabled,
        AiGlobalQuota globalQuota) {

    /**
     * @param snapshot service snapshot
     * @return the wire view
     */
    public static AiStatusResp from(AiStatusSnapshot snapshot) {
        return new AiStatusResp(snapshot.enabled(), snapshot.indexing(), snapshot.indexed(),
                snapshot.files(), snapshot.chunks(), snapshot.indexedAt(), snapshot.durationMs(),
                snapshot.error(), snapshot.collection(), snapshot.chatModel(),
                snapshot.embeddingModel(), snapshot.topK(), snapshot.similarityThreshold(),
                snapshot.webSearchEnabled(), snapshot.dailyTokenLimit(), snapshot.chatEnabled(),
                snapshot.agentEnabled(), snapshot.documentSearchEnabled(),
                snapshot.codeRunnerEnabled(), snapshot.skillsEnabled(), snapshot.globalQuota());
    }
}
