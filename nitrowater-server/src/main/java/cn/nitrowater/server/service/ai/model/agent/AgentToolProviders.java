package cn.nitrowater.server.service.ai.model.agent;

import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Groups the tool providers so the agent can add the optional web tool only when the user opted
 * in, without relying on bean-type injection ambiguity.
 *
 * @param core always-advertised tools ({@code searchDocs}, {@code loadSkill}, {@code runCode})
 * @param web the optional {@code webSearch} tool
 */
public record AgentToolProviders(ToolCallbackProvider core, ToolCallbackProvider web) {
}
