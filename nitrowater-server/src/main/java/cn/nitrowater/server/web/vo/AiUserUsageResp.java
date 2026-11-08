package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiUserUsageRow;

/**
 * Administrator-facing row combining one user's UTC-day token usage with its display identity.
 *
 * @param uid account uid
 * @param username login name mirror, may be {@code null} before the first profile sync
 * @param nickname display name mirror, may be {@code null}
 * @param usage the raw usage counters
 */
public record AiUserUsageResp(long uid, String username, String nickname, AiUsageCounters usage) {

    /**
     * @param row service row
     * @return the wire view
     */
    public static AiUserUsageResp from(AiUserUsageRow row) {
        return new AiUserUsageResp(row.uid(), row.username(), row.nickname(),
                AiUsageCounters.from(row.usage()));
    }
}
