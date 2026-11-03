package cn.nitrowater.server.service.ai.model.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * The one JSON shape every agent tool hands back to the model.
 *
 * <p>Same envelope as the HTTP side ({@code ApiResponse}): {@code success} / {@code code} /
 * {@code message} / {@code data}. {@code success} and {@code retryable} are derived from
 * {@link ToolCode}, so the payload the model reads, the trace entry the UI renders and the step
 * badge can never disagree.</p>
 *
 * <p>{@code data} is deliberately dynamic: each tool puts its own payload there (results, stdout,
 * queue depth...) and failures may carry data too (e.g. the list of valid skill names). {@code
 * message} is the single sentence the model should act on; it is omitted when {@code null}.</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolResponse {

    private final boolean success;
    private final String code;
    private final String message;
    private final Boolean retryable;
    private final Map<String, Object> data;

    /** @return a bare success with no payload */
    public static ToolResponse ok() {
        return of(ToolCode.OK);
    }

    /**
     * @param data tool payload
     * @return a success carrying {@code data}
     */
    public static ToolResponse ok(Map<String, Object> data) {
        return of(ToolCode.OK, null, data);
    }

    /**
     * @param data    tool payload
     * @param message sentence the model should act on (e.g. "no local match, these are web results")
     * @return a success carrying {@code data} and a note
     */
    public static ToolResponse ok(Map<String, Object> data, String message) {
        return of(ToolCode.OK, message, data);
    }

    /**
     * @param code outcome code
     * @return a response without payload or note
     */
    public static ToolResponse of(ToolCode code) {
        return of(code, null, null);
    }

    /**
     * @param code    outcome code
     * @param message sentence the model should act on
     * @return a response without payload
     */
    public static ToolResponse of(ToolCode code, String message) {
        return of(code, message, null);
    }

    /**
     * @param code    outcome code
     * @param message sentence the model should act on, or {@code null}
     * @param data    tool payload, or {@code null}
     * @return the response; {@code success}/{@code retryable} come from {@code code}
     */
    public static ToolResponse of(ToolCode code, String message, Map<String, Object> data) {
        return new ToolResponse(code.isSuccess(), code.getCode(), message, code.getRetryable(), data);
    }

    /**
     * Records this outcome in the turn trace so the UI shows what actually happened, then returns
     * {@code this} so a tool can answer with one expression.
     *
     * @param trace       turn trace, may be {@code null} when no tool context was provided
     * @param name        tool name
     * @param argument    main argument (truncated by the trace)
     * @param resultCount results produced
     * @return this response
     */
    public ToolResponse record(AgentTrace trace, String name, String argument, int resultCount) {
        if (trace != null) {
            trace.recordTool(name, argument, resultCount, code, message);
        }
        return this;
    }

    /** @return the JSON payload handed to the model */
    public String json() {
        return JsonUtil.toJson(this);
    }
}
