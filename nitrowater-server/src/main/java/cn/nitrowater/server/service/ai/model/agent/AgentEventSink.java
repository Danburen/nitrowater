package cn.nitrowater.server.service.ai.model.agent;

/**
 * Transport of one streamed agent turn.
 *
 * <p>The turn pipeline owns the protocol (which event carries what); the web layer owns the
 * connection (SSE frames, timeouts, async request release). This interface is the only thing the
 * service knows about the transport, so the turn can be driven without importing Spring MVC
 * types.</p>
 */
public interface AgentEventSink {

    /**
     * Writes one named event.
     *
     * @param event protocol event name ({@code conversation}, {@code start}, {@code step},
     *        {@code delta}, {@code sources}, {@code tools}, {@code skills}, {@code done} or
     *        {@code error})
     * @param payload event body; the transport serialises it
     * @return whether the frame was written; {@code false} means the client is gone
     */
    boolean send(String event, Object payload);

    /** Closes the response once the turn is fully persisted and flushed. */
    void complete();
}
