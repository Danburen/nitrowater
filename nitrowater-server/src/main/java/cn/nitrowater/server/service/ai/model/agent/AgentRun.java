package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.agent.AiStreamChunk;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Outbound stream of one agent turn: the single place answer tokens and progress steps are pushed
 * into.
 *
 * <p>Every emitter of a turn shares this object — the model rounds push {@code text}, the step
 * markers push {@code step}, and instrumented tool calls push their own start/end markers. That
 * keeps ordering in one serialized sink instead of one queue per producer, and it removes the need
 * for a transport-level "pre-flight" request: the browser sees the turn begin, each tool run, and
 * the answer text on a single ordered stream.</p>
 *
 * <p>Emissions before the consumer subscribes are rejected by the unicast sink and ignored here:
 * the transport attaches immediately, and a lost pre-subscription frame would have no reader
 * anyway. Emission happens on the model/tool thread; the sink is not shared across threads.</p>
 */
public final class AgentRun {

    private final Sinks.Many<AiStreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();

    /** Model subscription of the round currently running, so cancellation can stop it. */
    private final AtomicReference<Disposable> active = new AtomicReference<>();

    /**
     * @return the turn's event stream; cancelling it stops the round in flight
     */
    public Flux<AiStreamChunk> events() {
        return this.sink.asFlux().doOnCancel(() -> {
            Disposable running = this.active.get();
            if (running != null) {
                running.dispose();
            }
        });
    }

    /**
     * @param chunk element to publish
     */
    void emit(AiStreamChunk chunk) {
        this.sink.tryEmitNext(chunk);
    }

    /**
     * @param token answer delta; empty tokens are dropped
     */
    void text(String token) {
        if (!token.isEmpty()) {
            emit(new AiStreamChunk(token, null, null, null));
        }
    }

    /**
     * @param step progress marker to publish
     */
    public void step(AiStreamChunk.Step step) {
        emit(new AiStreamChunk("", null, null, step));
    }

    /**
     * @param disposable model subscription of the round that just started
     */
    void track(Disposable disposable) {
        this.active.set(disposable);
    }

    /** Ends the stream normally. */
    void complete() {
        this.sink.tryEmitComplete();
    }

    /**
     * @param error failure that aborts the turn
     */
    void fail(Throwable error) {
        this.sink.tryEmitError(error);
    }
}
