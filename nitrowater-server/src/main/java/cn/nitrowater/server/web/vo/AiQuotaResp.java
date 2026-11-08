package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiSlashQuota;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Card payload for {@code GET /api/ai/slash/quota}.
 *
 * @param dailyTokenLimit configured ceiling
 * @param totalTokens used tokens ({@code input + output})
 * @param inputTokens prompt-side usage
 * @param outputTokens completion-side usage
 * @param requestCount requests admitted today
 * @param date UTC day the counters belong to
 * @param resetAt next UTC midnight
 */
public record AiQuotaResp(long dailyTokenLimit, long totalTokens, long inputTokens, long outputTokens,
        int requestCount, LocalDate date, Instant resetAt) {

    /**
     * @param quota service view
     * @return the wire view
     */
    public static AiQuotaResp from(AiSlashQuota quota) {
        return new AiQuotaResp(quota.dailyTokenLimit(), quota.totalTokens(), quota.inputTokens(),
                quota.outputTokens(), quota.requestCount(), quota.date(), quota.resetAt());
    }
}
