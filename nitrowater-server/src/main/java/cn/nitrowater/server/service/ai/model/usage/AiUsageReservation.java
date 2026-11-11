package cn.nitrowater.server.service.ai.model.usage;

import java.time.LocalDate;

/**
 * A quota hold pinned to the UTC date on which the request was admitted.
 *
 * <p>{@code limit} and {@code usedBefore} are the snapshot taken while the hold was granted: they let
 * the caller answer "how much budget is left right now" without a second database read.</p>
 *
 * @param uid account uid
 * @param date UTC day the hold belongs to
 * @param reservedTokens tokens held for the call
 * @param estimatedInputTokens estimated prompt tokens
 * @param global whether the hold also counts against the platform-wide ceiling
 * @param limit the per-user daily limit that was in force at admission
 * @param usedBefore the day's input+output+reserved *before* this hold was added
 */
public record AiUsageReservation(long uid, LocalDate date, long reservedTokens,
        long estimatedInputTokens, boolean global, long limit, long usedBefore) {
}
