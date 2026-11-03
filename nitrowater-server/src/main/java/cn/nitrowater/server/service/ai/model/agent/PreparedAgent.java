package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.model.AiStreamChunk;
import cn.nitrowater.server.service.ai.AiUsageTracker;
import reactor.core.publisher.Flux;

/**
 * A streamed agent turn: the trace is filled while the tool loop runs, then the final answer
 * tokens are emitted.
 *
 * @param trace per-turn tool/source collector
 * @param chunks answer stream with optional provider usage metadata
 * @param usageSession quota reservation and usage meter for this turn
 */
public record PreparedAgent(
        AgentTrace trace,
        Flux<AiStreamChunk> chunks,
        AiUsageTracker usageSession
) {}
