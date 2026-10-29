package cn.nitrowater.server.ai;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * A retrieval result ready to be streamed.
 *
 * @param sources citations resolved before the first token (so the UI can render them first)
 * @param chunks answer stream; a fixed "not found" chunk when the answer is not grounded
 * @param grounded whether retrieval produced usable context
 * @param usageSession quota reservation and usage meter for this turn
 */
public record PreparedChat(List<DocSource> sources, Flux<AiStreamChunk> chunks, boolean grounded,
        Optional<AiUsageTracker> usageSession) {
}
