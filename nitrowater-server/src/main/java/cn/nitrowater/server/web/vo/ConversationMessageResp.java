package cn.nitrowater.server.web.vo;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.service.ai.model.rag.DocSource;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import tools.jackson.core.type.TypeReference;

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
 * @param skills skills loaded when the answer was produced
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
        List<String> skills,
        String model,
        boolean moderated,
        Instant createdAt) {

    private static final TypeReference<List<DocSource>> SOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ToolCall>> TOOLS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> SKILLS_TYPE = new TypeReference<>() {
    };

    /**
     * @param message stored message row
     * @return the wire view, with the JSON trace columns parsed back into lists
     */
    public static ConversationMessageResp from(AiMessage message) {
        return new ConversationMessageResp(
                message.getUuid(),
                message.getSeq(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                JsonUtil.parseList(message.getSourcesJson(), SOURCES_TYPE),
                JsonUtil.parseList(message.getToolsJson(), TOOLS_TYPE),
                JsonUtil.parseList(message.getSkillsJson(), SKILLS_TYPE),
                message.getModel(),
                Boolean.TRUE.equals(message.getModerated()),
                message.getCreatedAt());
    }
}
