package cn.nitrowater.server.service.ai.model;

import java.time.LocalDate;

/**
 * One day's platform-wide totals, used by the admin summary series.
 *
 * @param date UTC day
 * @param inputTokens prompt tokens
 * @param outputTokens completion tokens
 * @param requestCount provider calls
 * @param estimatedRequests calls whose usage was estimated
 */
public record AiDailyTotal(LocalDate date, long inputTokens, long outputTokens, int requestCount,
        int estimatedRequests) {

    /** @return tokens actually consumed that day */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
