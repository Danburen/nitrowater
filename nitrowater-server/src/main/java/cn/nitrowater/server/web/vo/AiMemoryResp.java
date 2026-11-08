package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.MemoryView;

import java.time.Instant;

/**
 * One long-term memory returned by {@code GET /api/ai/memories}.
 *
 * @param uuid memory uuid
 * @param kind {@code FACT} / {@code PREFERENCE} / {@code DECISION} / {@code PROJECT}
 * @param content memory text
 * @param confidence confidence 0-100
 * @param updatedAt last change
 * @param lastUsedAt last recall, or {@code null}
 */
public record AiMemoryResp(String uuid, String kind, String content, short confidence,
        Instant updatedAt, Instant lastUsedAt) {

    /**
     * @param memory service view
     * @return the wire view
     */
    public static AiMemoryResp from(MemoryView memory) {
        return new AiMemoryResp(memory.uuid(), memory.kind(), memory.content(), memory.confidence(),
                memory.updatedAt(), memory.lastUsedAt());
    }
}
