package cn.nitrowater.server.service.ai.model;

import java.time.Instant;

/**
 * Runtime status snapshot returned by {@code GET /api/ai/status}.
 *
 * @param enabled master switch (configuration AND the administrator's switch)
 * @param indexing whether a rebuild is running right now
 * @param indexed whether the corpus currently has points
 * @param files markdown files read by the last rebuild
 * @param chunks indexed chunks
 * @param indexedAt when the last rebuild finished
 * @param durationMs duration of the last rebuild
 * @param error failure message of the last rebuild, or {@code null}
 * @param collection vector collection name
 * @param chatModel chat model id
 * @param embeddingModel embedding model id
 * @param topK configured retrieval size
 * @param similarityThreshold configured similarity threshold
 * @param webSearchEnabled whether the optional web-search tool is available
 * @param dailyTokenLimit per-user daily token limit
 * @param chatEnabled whether chat mode is enabled
 * @param agentEnabled whether agent mode is enabled
 * @param documentSearchEnabled whether local documentation retrieval is enabled
 * @param codeRunnerEnabled whether the code runner is enabled
 * @param skillsEnabled whether skills are available
 * @param globalQuota platform-wide daily budget snapshot
 */
public record AiStatusSnapshot(
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
}
