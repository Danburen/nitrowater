package cn.nitrowater.server.web.dto;

import java.time.Instant;

/**
 * Result of an admin re-index.
 *
 * @param files number of markdown files read
 * @param chunks number of embedded and stored chunks
 * @param indexedAt completion instant
 * @param durationMs total duration
 */
public record AiReindexResp(int files, int chunks, Instant indexedAt, long durationMs) {
}
