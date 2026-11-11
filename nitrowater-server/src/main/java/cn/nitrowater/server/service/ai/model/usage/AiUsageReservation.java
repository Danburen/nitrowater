package cn.nitrowater.server.service.ai.model.usage;

import java.time.LocalDate;

/**
 * A quota hold pinned to the UTC date on which the request was admitted.
 *
 * @param uid account uid
 * @param date UTC day the hold belongs to
 * @param reservedTokens tokens held for the call
 * @param estimatedInputTokens estimated prompt tokens
 * @param global whether the hold also counts against the platform-wide ceiling
 */
public record AiUsageReservation(long uid, LocalDate date, long reservedTokens,
        long estimatedInputTokens, boolean global) {
}
