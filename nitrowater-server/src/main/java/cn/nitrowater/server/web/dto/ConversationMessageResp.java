package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.ai.DocSource;
import cn.nitrowater.server.ai.agent.ToolCall;

import java.time.Instant;
import java.util.List;

/**
 * One stored message.
 *
 * @param uuid external message id
 * @param seq position within the conversation, starting at 1
 * @param role {@code user}, {@code assistant} or {@code error}
 * @param content message text (original, never pruned)
 * @param sources citations captured when the answer was produced
 * @param tools tool trace captured when the answer was produced
 * @param model model that produced the answer, or {@code null}
 * @param moderated whether the stored text was masked by the content gate
 * @param createdAt creation timestamp
 */
public record ConversationMessageResp(
        String uuid,
        int seq,
        String role,
        String content,
        List<DocSource> sources,
        List<ToolCall> tools,
        String model,
        boolean moderated,
        Instant createdAt) {
}
