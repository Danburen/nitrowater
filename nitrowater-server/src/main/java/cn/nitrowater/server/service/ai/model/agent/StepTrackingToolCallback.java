package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Wraps one tool callback so every invocation publishes a live {@code tool_start}/{@code tool_end}
 * pair on the turn's {@link AgentRun}.
 *
 * <p>Instrumenting at the callback boundary is what gives the UI <em>per-call</em> granularity:
 * Spring AI's {@code ToolCallingManager} executes a round's calls in one batch and publishes nothing
 * between them, so a batch-level marker would show every tool of a round starting and finishing
 * together. The wrapper also carries the real argument (the raw tool input JSON) and measures the
 * duration, neither of which exists at batch level.</p>
 *
 * <p>Result metadata (status, result count) is read back from {@link AgentTrace}: tools already
 * record it, so the wrapper stays free of tool-specific knowledge.</p>
 */
final class StepTrackingToolCallback implements ToolCallback {

    /** Argument keys that make a readable card title, in priority order. */
    private static final List<String> ARGUMENT_KEYS =
            List.of("query", "name", "language", "kind", "content", "code", "stdin");

    private static final int MAX_ARGUMENT_CHARS = 80;

    private final ToolCallback delegate;
    private final AgentRun run;
    private final AgentTrace trace;
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * @param delegate tool to invoke
     * @param run      turn stream that receives the step markers
     * @param trace    turn trace the delegate records its call into
     */
    StepTrackingToolCallback(ToolCallback delegate, AgentRun run, AgentTrace trace) {
        this.delegate = delegate;
        this.run = run;
        this.trace = trace;
    }

    @NotNull
    @Override
    public ToolDefinition getToolDefinition() {
        return this.delegate.getToolDefinition();
    }

    @NotNull
    @Override
    public ToolMetadata getToolMetadata() {
        return this.delegate.getToolMetadata();
    }

    @NotNull
    @Override
    public String call(@NotNull String toolInput) {
        return call(toolInput, null);
    }

    @NotNull
    @Override
    public String call(@NotNull String toolInput, ToolContext toolContext) {
        String tool = this.delegate.getToolDefinition().name();
        String callId = tool + "#" + this.sequence.incrementAndGet();
        this.run.step(AiStreamChunk.Step.toolStart(tool, callId, summarise(toolInput)));
        int recorded = this.trace.callCount();
        long startedAt = System.nanoTime();
        try {
            String result = this.delegate.call(toolInput, toolContext);
            ToolCall call = this.trace.lastCallFrom(recorded);
            this.run.step(AiStreamChunk.Step.toolEnd(tool, callId,
                    call == null ? ToolCode.OK.getCode() : call.status(),
                    call == null ? 0 : call.resultCount(),
                    elapsedMillis(startedAt)));
            return result;
        } catch (RuntimeException ex) {
            this.run.step(AiStreamChunk.Step.toolEnd(tool, callId, ToolCode.UNAVAILABLE.getCode(), 0,
                    elapsedMillis(startedAt)));
            throw ex;
        }
    }

    /**
     * Turns the raw tool input JSON into the one argument worth showing on the card.
     *
     * <p>Model-generated JSON is not trusted to have any particular shape, so the first known key
     * wins and anything unparsable falls back to the raw text — the card must never fail because of
     * its subtitle.</p>
     *
     * @param toolInput raw tool input JSON, may be {@code null}
     * @return a short human-readable argument, or {@code null} when there is none
     */
    private static String summarise(String toolInput) {
        if (StringUtil.isBlank(toolInput)) {
            return null;
        }
        try {
            Map<?, ?> arguments = JsonUtil.fromJson(toolInput, Map.class);
            if (arguments != null) {
                for (String key : ARGUMENT_KEYS) {
                    String value = text(arguments.get(key));
                    if (value != null) {
                        return truncate(value);
                    }
                }
                for (Object value : arguments.values()) {
                    String text = text(value);
                    if (text != null) {
                        return truncate(text);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Not JSON (or not an object): the raw text below is still better than nothing.
        }
        return truncate(toolInput);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ARGUMENT_CHARS
                ? value
                : value.substring(0, MAX_ARGUMENT_CHARS) + "…";
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
