package cn.nitrowater.server.service.ai.chat;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Card payload for {@code /quota} and {@code GET /api/ai/slash/quota}.
 *
 * @param dailyTokenLimit configured ceiling
 * @param totalTokens used tokens ({@code input + output})
 * @param inputTokens prompt-side usage
 * @param outputTokens completion-side usage
 * @param requestCount requests admitted today
 * @param date UTC day the counters belong to
 * @param resetAt next UTC midnight
 */
public record AiSlashQuota(long dailyTokenLimit, long totalTokens, long inputTokens, long outputTokens,
        int requestCount, LocalDate date, Instant resetAt) {
}
