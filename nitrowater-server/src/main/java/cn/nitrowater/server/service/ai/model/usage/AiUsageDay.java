package cn.nitrowater.server.service.ai.model.usage;

import java.time.LocalDate;

/**
 * One day of platform-wide usage.
 *
 * @param date UTC day
 * @param inputTokens prompt tokens
 * @param outputTokens completion tokens
 * @param totalTokens input + output
 * @param requestCount provider calls
 */
public record AiUsageDay(LocalDate date, long inputTokens, long outputTokens, long totalTokens,
        int requestCount) {
}
