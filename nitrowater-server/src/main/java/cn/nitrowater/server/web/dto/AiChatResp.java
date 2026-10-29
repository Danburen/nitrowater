package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.ai.DocSource;

import java.util.List;

/**
 * Chat response.
 *
 * @param conversationUuid conversation that owns the turn (newly created when none was supplied)
 * @param answer answer text
 * @param sources citations referenced by the answer (empty for the fixed "not found" reply)
 * @param grounded whether the answer used retrieved documentation
 */
public record AiChatResp(String conversationUuid, String answer, List<DocSource> sources, boolean grounded) {
}
