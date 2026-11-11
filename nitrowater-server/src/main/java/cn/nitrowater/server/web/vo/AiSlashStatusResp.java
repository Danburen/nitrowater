package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.chat.AiSlashStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Card payload for {@code GET /api/ai/slash/status}: the self-check snapshot shown to users.
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
public record AiSlashStatusResp(String chatModel, String embeddingModel, boolean indexing,
        boolean indexed, int files, int chunks, Instant indexedAt, String indexError,
        Map<String, Boolean> features, List<String> tools, List<String> skills,
        long dailyTokenLimit) {

    /**
     * @param status service view
     * @return the wire view
     */
    public static AiSlashStatusResp from(AiSlashStatus status) {
        return new AiSlashStatusResp(status.chatModel(), status.embeddingModel(), status.indexing(),
                status.indexed(), status.files(), status.chunks(), status.indexedAt(),
                status.indexError(), status.features(), status.tools(), status.skills(),
                status.dailyTokenLimit());
    }
}
