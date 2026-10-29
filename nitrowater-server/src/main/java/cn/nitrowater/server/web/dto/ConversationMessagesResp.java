package cn.nitrowater.server.web.dto;

import java.util.List;

/**
 * A page of messages in ascending sequence order.
 *
 * @param items messages
 * @param hasMore whether older messages exist (pass {@code items[0].seq} as {@code before})
 */
public record ConversationMessagesResp(List<ConversationMessageResp> items, boolean hasMore) {
}
