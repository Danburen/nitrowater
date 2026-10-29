package cn.nitrowater.server.ai;

import cn.nitrowater.server.ai.agent.AgentToolProviders;
import cn.nitrowater.server.ai.tool.DocTools;
import cn.nitrowater.server.ai.tool.RunCodeTool;
import cn.nitrowater.server.ai.tool.SkillTools;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
import cn.nitrowater.server.ai.tool.WebSearchTool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the agent's tool catalog.
 *
 * <p>Tool discovery uses Spring AI's {@link MethodToolCallbackProvider}: every {@code @Tool} method
 * on the tool beans becomes a {@code ToolCallback}.</p>
 *
 * <p>There is deliberately no {@code ChatClient} bean anymore. Spring AI 2.0's
 * {@code ToolCallingAdvisor} aggregates the whole model stream before emitting anything whenever
 * tool options are present, which turns every answer into a single SSE delta. The assistant talks
 * to {@code ChatModel} directly and drives the tool loop itself so tokens stay incremental; see
 * {@code AiAgentService}.</p>
 */
@Configuration
@EnableConfigurationProperties({AiProperties.class, WebSearchProperties.class})
public class AiConfig {

    /**
     * @param docTools {@code searchDocs}
     * @param skillTools {@code loadSkill}
     * @param runCodeTool {@code runCode}
     * @param webSearchTool {@code webSearch}
     * @return core tools (always available) and the optional web tool
     */
    @Bean
    public AgentToolProviders agentToolProviders(DocTools docTools, SkillTools skillTools,
            RunCodeTool runCodeTool, WebSearchTool webSearchTool) {
        return new AgentToolProviders(
                MethodToolCallbackProvider.builder()
                        .toolObjects(docTools, skillTools, runCodeTool)
                        .build(),
                MethodToolCallbackProvider.builder().toolObjects(webSearchTool).build());
    }
}
