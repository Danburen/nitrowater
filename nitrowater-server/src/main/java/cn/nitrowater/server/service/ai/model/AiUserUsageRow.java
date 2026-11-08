package cn.nitrowater.server.service.ai.model;

/**
 * One usage row joined with its display identity.
 *
 * @param uid account uid
 * @param username login name mirror, may be {@code null}
 * @param nickname display name mirror, may be {@code null}
 * @param usage raw usage counters
 */
public record AiUserUsageRow(long uid, String username, String nickname, AiUserUsage usage) {
}
