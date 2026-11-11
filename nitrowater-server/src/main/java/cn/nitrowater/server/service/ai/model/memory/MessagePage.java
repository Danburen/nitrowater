package cn.nitrowater.server.service.ai.model.memory;

import cn.nitrowater.server.infra.entity.AiMessage;

import java.util.List;

/**
 * A page of messages in ascending sequence order.
 *
 * @param items messages
 * @param hasMore whether older messages exist
 */
public record MessagePage(List<AiMessage> items, boolean hasMore) {
}
