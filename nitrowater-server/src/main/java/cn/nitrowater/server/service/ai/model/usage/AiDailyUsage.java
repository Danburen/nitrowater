package cn.nitrowater.server.service.ai.model.usage;

import java.time.LocalDate;

/** Daily per-user token counters. The usage date is UTC. */
public record AiDailyUsage(
        LocalDate date,
        long inputTokens,
        long outputTokens,
        long reservedTokens,
        int requestCount,
        int estimatedRequests,
        long dailyTokenLimit,
        long totalTokens) {
}
