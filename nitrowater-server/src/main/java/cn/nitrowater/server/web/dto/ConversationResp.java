package cn.nitrowater.server.web.dto;

import java.time.Instant;

/**
 * Conversation list item.
 *
 * @param uuid external conversation id
 * @param title display title (derived from the first question when not renamed)
 * @param messageCount number of stored messages
 * @param lastMessageAt ordering timestamp
 * @param createdAt creation timestamp
 * @param status {@code ACTIVE} or {@code ARCHIVED}
 */
public record ConversationResp(
        String uuid,
        String title,
        int messageCount,
        Instant lastMessageAt,
        Instant createdAt,
        String status) {
}
