package cn.nitrowater.server.service.ai.model.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashMap;
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

    /**
     * Ceiling of one tool result, in JSON characters.
     *
     * <p>Tool results are replayed on every later round of the same turn, so an oversized payload is
     * paid for repeatedly. Individual tools cap their own fields; this is the backstop for the one
     * that forgets, and for a payload that is legitimately huge (the sandbox returns up to 64KB per
     * stream). 16K characters stays well inside the 64K replay budget next to the system prompt and
     * the history.</p>
     */
    private static final int MAX_JSON_CHARS = 16_000;

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
        String json = JsonUtil.toJson(this);
        if (json.length() <= MAX_JSON_CHARS) {
            return json;
        }
        // Every model round resends the tool results, so one oversized payload is paid for by every
        // later round of the turn. Tools cap their own fields, but a new tool can forget to and the
        // sandbox alone may return 64KB per stream; this is the single ceiling that cannot be
        // forgotten. The envelope stays valid (the preview is a string field), so the model still
        // reads success/code/message and can ask for a narrower query.
        int head = MAX_JSON_CHARS / 2;
        int tail = MAX_JSON_CHARS / 2;
        String preview = json.substring(0, head)
                + "\n…（工具结果过长，已截断 " + (json.length() - head - tail) + " 字符）…\n"
                + json.substring(json.length() - tail);
        Map<String, Object> capped = new LinkedHashMap<>();
        capped.put("truncated", true);
        capped.put("originalChars", json.length());
        capped.put("preview", preview);
        return JsonUtil.toJson(new ToolResponse(success, code, message, retryable, capped));
    }
}
