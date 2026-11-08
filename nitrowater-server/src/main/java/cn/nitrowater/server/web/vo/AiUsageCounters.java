package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiUserUsage;

import java.time.LocalDate;

/**
 * Usage counters of one user for one UTC day, as nested inside {@link AiUserUsageResp}.
 *
 * @param date UTC day
 * @param inputTokens prompt tokens
 * @param outputTokens completion tokens
 * @param reservedTokens in-flight token reservations
 * @param requestCount settled provider calls
 * @param estimatedRequests calls whose usage was estimated
 * @param dailyTokenLimit per-user daily ceiling
 * @param totalTokens input + output
 */
public record AiUsageCounters(
        LocalDate date,
        long inputTokens,
        long outputTokens,
        long reservedTokens,
        int requestCount,
        int estimatedRequests,
        long dailyTokenLimit,
        long totalTokens) {

    /**
     * @param usage service counters (the {@code uid} is carried by the parent row)
     * @return the wire view
     */
    public static AiUsageCounters from(AiUserUsage usage) {
        return new AiUsageCounters(usage.date(), usage.inputTokens(), usage.outputTokens(),
                usage.reservedTokens(), usage.requestCount(), usage.estimatedRequests(),
                usage.dailyTokenLimit(), usage.totalTokens());
    }
}
