package cn.nitrowater.server.service.ai.model;

import cn.nitrowater.server.service.ai.model.agent.ToolCall;

import java.util.List;

/**
 * Outcome of a non-streamed agent turn.
 *
 * @param conversationUuid conversation that owns the turn (newly created when none was supplied)
 * @param answer final screened answer
 * @param sources citations collected by the tools
 * @param tools tool calls in execution order
 * @param skills skills loaded during the turn
 * @param grounded whether any evidence was collected
 */
public record AgentTurnResult(
        String conversationUuid,
        String answer,
        List<DocSource> sources,
        List<ToolCall> tools,
        List<String> skills,
        boolean grounded) {
}
