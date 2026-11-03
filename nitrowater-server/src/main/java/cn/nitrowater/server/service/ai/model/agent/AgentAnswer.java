package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.DocSource;

import java.util.List;

/**
 * A complete agent turn.
 *
 * @param answer final answer text
 * @param sources citations collected from the tools (docs and optional web results)
 * @param tools tool calls in execution order
 * @param skills skills loaded during the turn
 * @param grounded whether any tool evidence was collected
 * @param usage provider-reported usage or a marked fallback estimate
 */
public record AgentAnswer(
        String answer,
        List<DocSource> sources,
        List<ToolCall> tools,
        List<String> skills,
        boolean grounded,
        AiTokenUsage usage) {
}
