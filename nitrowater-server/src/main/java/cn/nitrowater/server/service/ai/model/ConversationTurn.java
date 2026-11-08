package cn.nitrowater.server.service.ai.model;

import cn.nitrowater.server.infra.entity.AiConversation;

import java.util.List;

/**
 * A conversation plus the history captured before the current question.
 *
 * @param conversation the conversation row
 * @param history previous turns to feed the prompt, oldest first
 */
public record ConversationTurn(AiConversation conversation, List<ChatTurn> history) {
}
