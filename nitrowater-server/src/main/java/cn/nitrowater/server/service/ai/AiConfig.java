package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.properties.AiProperties;
import cn.nitrowater.server.service.ai.properties.ModerationProperties;
import cn.nitrowater.server.service.ai.properties.WebSearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the AI configuration properties.
 *
 * <p>Tool wiring lives in {@code AgentToolRegistry}: it scans the tool beans for their
 * {@code @Tool} methods and keeps the gates next to them. This class intentionally does not build a
 * tool provider any more.</p>
 *
 * <p>There is deliberately no {@code ChatClient} bean. Spring AI 2.0's
 * {@code ToolCallingAdvisor} aggregates the whole model stream before emitting anything whenever
 * tool options are present, which turns every answer into a single SSE delta. The assistant talks
 * to {@code ChatModel} directly and drives the tool loop itself so tokens stay incremental; see
 * {@code AiAgentService}.</p>
 */
@Configuration
@EnableConfigurationProperties({AiProperties.class, WebSearchProperties.class, ModerationProperties.class})
public class AiConfig {
}
