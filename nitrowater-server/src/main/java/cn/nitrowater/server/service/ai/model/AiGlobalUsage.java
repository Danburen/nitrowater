package cn.nitrowater.server.service.ai.model;

import java.time.LocalDate;

/**
 * All-users view of one UTC day, including the breaker state.
 *
 * @param date UTC day
 * @param inputTokens prompt tokens
 * @param outputTokens completion tokens
 * @param reservedTokens in-flight token reservations
 * @param requestCount provider calls recorded today
 * @param dailyTokenLimit platform-wide ceiling ({@code 0} = unlimited)
 * @param enabled whether the breaker is switched on
 * @param tripped whether new model calls are rejected right now
 */
public record AiGlobalUsage(LocalDate date, long inputTokens, long outputTokens, long reservedTokens,
        int requestCount, long dailyTokenLimit, boolean enabled, boolean tripped) {

    /** @return tokens actually consumed today (reservations excluded) */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
