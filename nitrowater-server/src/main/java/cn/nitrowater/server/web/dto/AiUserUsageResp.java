package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.AiUsageService;

import java.time.LocalDate;

/**
 * Administrator-facing row combining one user's UTC-day token usage with its display identity.
 *
 * @param uid account uid
 * @param username login name mirror, may be {@code null} before the first profile sync
 * @param nickname display name mirror, may be {@code null}
 * @param usage the raw usage counters
 */
public record AiUserUsageResp(long uid, String username, String nickname, Usage usage) {

    /** Usage counters for one user and UTC day. */
    public record Usage(
            LocalDate date,
            long inputTokens,
            long outputTokens,
            long reservedTokens,
            int requestCount,
            int estimatedRequests,
            long dailyTokenLimit,
            long totalTokens) {

        /**
         * @param source accounting row
         * @return the serialized shape consumed by the admin console
         */
        public static Usage from(AiUsageService.UserUsage source) {
            return new Usage(source.date(), source.inputTokens(), source.outputTokens(),
                    source.reservedTokens(), source.requestCount(), source.estimatedRequests(),
                    source.dailyTokenLimit(), source.totalTokens());
        }
    }
}
