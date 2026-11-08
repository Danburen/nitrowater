package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiDailyUsage;

import java.time.LocalDate;

/**
 * The caller's UTC-day token usage returned by {@code GET /api/ai/usage}.
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
public record AiUsageResp(
        LocalDate date,
        long inputTokens,
        long outputTokens,
        long reservedTokens,
        int requestCount,
        int estimatedRequests,
        long dailyTokenLimit,
        long totalTokens) {

    /**
     * @param usage service counters
     * @return the wire view
     */
    public static AiUsageResp from(AiDailyUsage usage) {
        return new AiUsageResp(usage.date(), usage.inputTokens(), usage.outputTokens(),
                usage.reservedTokens(), usage.requestCount(), usage.estimatedRequests(),
                usage.dailyTokenLimit(), usage.totalTokens());
    }
}
