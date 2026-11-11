package cn.nitrowater.server.service.ai.model.usage;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * All-users daily budget, so the chat page can explain a closed composer.
 *
 * @param enabled whether the breaker is switched on and has a positive ceiling
 * @param limit daily ceiling in tokens ({@code 0} when unlimited)
 * @param used tokens consumed today, reservations excluded
 * @param tripped whether new model calls are currently rejected
 * @param resetAt instant the counter rolls over (next UTC midnight)
 */
public record AiGlobalQuota(boolean enabled, long limit, long used, boolean tripped, Instant resetAt) {

    /**
     * @param usage service usage snapshot
     * @return the read model, with the rollover instant derived from the usage date
     */
    public static AiGlobalQuota from(AiGlobalUsage usage) {
        Instant resetAt = usage.date().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new AiGlobalQuota(usage.enabled(), usage.dailyTokenLimit(), usage.totalTokens(),
                usage.tripped(), resetAt);
    }
}
