package cn.nitrowater.server.service.ai.model;

import java.time.Instant;

/**
 * Result of an admin re-index.
 *
 * @param files number of markdown files read
 * @param chunks number of embedded and stored chunks
 * @param indexedAt completion instant
 * @param durationMs total duration
 */
public record AiReindexResult(int files, int chunks, Instant indexedAt, long durationMs) {
}
