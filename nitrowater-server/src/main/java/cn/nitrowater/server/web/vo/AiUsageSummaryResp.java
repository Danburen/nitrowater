package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiUsageDay;
import cn.nitrowater.server.service.ai.model.AiUsageSummary;

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
        List<AiUsageDay> days) {

    /**
     * @param summary service view
     * @return the wire view
     */
    public static AiUsageSummaryResp from(AiUsageSummary summary) {
        return new AiUsageSummaryResp(summary.globalEnabled(), summary.globalDailyTokenLimit(),
                summary.todayTokens(), summary.todayRequests(), summary.tripped(), summary.today(),
                summary.days());
    }
}
