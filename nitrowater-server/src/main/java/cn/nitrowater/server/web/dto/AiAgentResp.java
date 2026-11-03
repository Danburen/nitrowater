package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.model.DocSource;
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
}
