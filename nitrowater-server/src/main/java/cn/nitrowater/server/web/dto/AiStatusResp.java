package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.AiUsageService;

import java.time.Instant;
import java.time.ZoneOffset;

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
 * @param collection Qdrant collection name
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
        GlobalQuota globalQuota) {

    /**
     * All-users daily budget, surfaced to every signed-in client so the chat page can explain a
     * closed composer without asking an admin endpoint.
     *
     * @param enabled whether the breaker is switched on and has a positive ceiling
     * @param limit daily ceiling in tokens ({@code 0} when unlimited)
     * @param used tokens consumed today, reservations excluded
     * @param tripped whether new model calls are currently rejected
     * @param resetAt instant the counter rolls over (next UTC midnight)
     */
    public record GlobalQuota(boolean enabled, long limit, long used, boolean tripped, Instant resetAt) {

        /**
         * @param usage service snapshot
         * @return the wire view, with the rollover instant derived from the usage date
         */
        public static GlobalQuota from(AiUsageService.GlobalUsage usage) {
            Instant resetAt = usage.date().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return new GlobalQuota(usage.enabled(), usage.dailyTokenLimit(), usage.totalTokens(),
                    usage.tripped(), resetAt);
        }
    }
}
