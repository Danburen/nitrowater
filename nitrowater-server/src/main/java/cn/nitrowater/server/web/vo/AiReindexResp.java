package cn.nitrowater.server.web.vo;

import java.time.Instant;

/**
 * Result of an admin re-index.
 *
 * <p>Four plain scalars from a single call site, so the controller builds it directly instead of
 * routing through a factory.</p>
 *
 * @param files number of markdown files read
 * @param chunks number of embedded and stored chunks
 * @param indexedAt completion instant
 * @param durationMs total duration
 */
public record AiReindexResp(int files, int chunks, Instant indexedAt, long durationMs) {
}
