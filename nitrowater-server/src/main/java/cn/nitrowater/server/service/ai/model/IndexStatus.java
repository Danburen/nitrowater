package cn.nitrowater.server.service.ai.model;

import java.time.Instant;

/**
 * Snapshot of the documentation index.
 *
 * @param indexed whether the corpus currently has points
 * @param files number of source markdown files read by the last rebuild
 * @param chunks number of indexed chunks
 * @param indexedAt when the last successful rebuild finished ({@code null} after a restart)
 * @param durationMs duration of the last rebuild
 * @param error failure message of the last rebuild ({@code null} on success)
 */
public record IndexStatus(boolean indexed, int files, int chunks, Instant indexedAt, long durationMs,
        String error) {

    /** @return the "never indexed" state */
    public static IndexStatus empty() {
        return new IndexStatus(false, 0, 0, null, 0L, null);
    }
}
