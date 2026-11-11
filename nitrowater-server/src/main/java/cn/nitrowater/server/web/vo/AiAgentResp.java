package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.agent.AgentTurnResult;
import cn.nitrowater.server.service.ai.model.rag.DocSource;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;

import java.util.List;

/**
 * Agent chat response.
 *
 * @param conversationUuid conversation that owns the turn (newly created when none was supplied)
 * @param answer final answer
 * @param sources citations collected by the tools
 * @param tools tool calls in execution order
 * @param skills skills loaded during the turn
 * @param grounded whether any evidence was collected
 */
public record AiAgentResp(
        String conversationUuid,
        String answer,
        List<DocSource> sources,
        List<ToolCall> tools,
        List<String> skills,
        boolean grounded) {

    /**
     * @param result service turn result
     * @return the wire view
     */
    public static AiAgentResp from(AgentTurnResult result) {
        return new AiAgentResp(result.conversationUuid(), result.answer(), result.sources(),
                result.tools(), result.skills(), result.grounded());
    }
}
