package cn.nitrowater.server.service.ai.model.moderation;

import java.time.Instant;

/**
 * One moderation hit joined with its display identity.
 *
 * @param id audit row id
 * @param uid owner uid
 * @param username account name, when the account still exists
 * @param nickname display name, when the account still exists
 * @param conversationUuid conversation that triggered the hit, or {@code null}
 * @param direction {@code INPUT} / {@code OUTPUT} / {@code MEMORY} / {@code PROFILE}
 * @param action {@code LOGGED} / {@code BLOCKED} / {@code MASKED}
 * @param words distinct matched terms, comma separated
 * @param hitCount number of distinct matched terms
 * @param createdAt when the hit was recorded
 */
public record AiModerationHitView(long id, long uid, String username, String nickname,
        String conversationUuid, String direction, String action, String words, int hitCount,
        Instant createdAt) {
}
