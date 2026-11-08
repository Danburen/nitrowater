package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.MessagePage;

import java.util.List;

/**
 * A page of messages in ascending sequence order.
 *
 * @param items messages
 * @param hasMore whether older messages exist (pass {@code items[0].seq} as {@code before})
 */
public record ConversationMessagesResp(List<ConversationMessageResp> items, boolean hasMore) {

    /**
     * @param page service page
     * @return the wire view
     */
    public static ConversationMessagesResp from(MessagePage page) {
        return new ConversationMessagesResp(
                page.items().stream().map(ConversationMessageResp::from).toList(), page.hasMore());
    }
}
