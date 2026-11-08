package cn.nitrowater.server.service.ai.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Platform-wide usage snapshot plus its daily series.
 *
 * @param globalEnabled whether the breaker is switched on AND has a positive ceiling
 * @param globalDailyTokenLimit configured ceiling in tokens ({@code 0} = unlimited)
 * @param todayTokens tokens consumed today (reservations excluded)
 * @param todayRequests provider calls recorded today
 * @param tripped whether new model calls are rejected right now
 * @param today the UTC day the snapshot belongs to
 * @param days per-day totals inside the requested window, oldest first
 */
public record AiUsageSummary(
        boolean globalEnabled,
        long globalDailyTokenLimit,
        long todayTokens,
        long todayRequests,
        boolean tripped,
        LocalDate today,
        List<AiUsageDay> days) {
}
