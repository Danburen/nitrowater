package cn.nitrowater.server.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Platform-wide (all users) usage for the admin console.
 *
 * <p>The daily series comes from the {@code uid = 0} sentinel row in {@code ai_daily_usage}, so it
 * uses the same reservation/settlement path as per-user counters instead of a parallel accounting
 * scheme.</p>
 *
 * @param globalEnabled whether the breaker is switched on AND has a positive ceiling
 * @param globalDailyTokenLimit configured ceiling in tokens ({@code 0} = unlimited)
 * @param todayTokens tokens consumed today (reservations excluded)
 * @param todayRequests provider calls recorded today
 * @param tripped whether new model calls are rejected right now
 * @param today the UTC day the snapshot belongs to
 * @param days per-day totals inside the requested window, oldest first
 */
public record AiUsageSummaryResp(
        boolean globalEnabled,
        long globalDailyTokenLimit,
        long todayTokens,
        long todayRequests,
        boolean tripped,
        LocalDate today,
        List<Day> days) {

    /**
     * One day of platform-wide usage.
     *
     * @param date UTC day
     * @param inputTokens prompt tokens
     * @param outputTokens completion tokens
     * @param totalTokens input + output
     * @param requestCount provider calls
     */
    public record Day(LocalDate date, long inputTokens, long outputTokens, long totalTokens, int requestCount) {
    }
}
