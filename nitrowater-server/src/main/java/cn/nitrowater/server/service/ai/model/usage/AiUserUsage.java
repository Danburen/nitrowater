package cn.nitrowater.server.service.ai.model.usage;

import java.time.LocalDate;

/**
 * Administrator view of one user's UTC-day usage.
 *
 * @param uid account uid
 * @param date UTC day
 * @param inputTokens prompt tokens
 * @param outputTokens completion tokens
 * @param reservedTokens in-flight token reservations
 * @param requestCount settled provider calls
 * @param estimatedRequests calls whose usage was estimated
 * @param dailyTokenLimit per-user daily ceiling
 * @param totalTokens input + output
 */
public record AiUserUsage(long uid, LocalDate date, long inputTokens, long outputTokens,
        long reservedTokens, int requestCount, int estimatedRequests, long dailyTokenLimit,
        long totalTokens) {
}
