package cn.nitrowater.server.service.ai.model;

import java.time.Instant;

/**
 * A long-term memory returned to the client.
 *
 * @param uuid memory uuid
 * @param kind {@code FACT} / {@code PREFERENCE} / {@code DECISION} / {@code PROJECT}
 * @param content memory text
 * @param confidence confidence 0-100
 * @param updatedAt last change
 * @param lastUsedAt last recall, or {@code null}
 */
public record MemoryView(String uuid, String kind, String content, short confidence,
        Instant updatedAt, Instant lastUsedAt) {
}
