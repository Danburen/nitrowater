package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.agent.AiStreamChunk;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Wraps one tool callback with the two things Spring AI cannot give us at batch level: a per-call
 * deadline and a live {@code tool_start}/{@code tool_end} pair on the turn's {@link AgentRun}.
 *
 * <p>Instrumenting at the callback boundary is what gives the UI <em>per-call</em> granularity:
 * Spring AI's {@code ToolCallingManager} executes a round's calls in one batch and publishes nothing
 * between them, so a batch-level marker would show every tool of a round starting and finishing
 * together. The wrapper also carries the real argument (the raw tool input JSON) and measures the
 * duration, neither of which exists at batch level.</p>
 *
 * <p>A deadline is a <em>result</em>, not an exception: the call is abandoned and the model receives
 * a retryable {@code tool.timeout} envelope, so it can narrow the arguments and try once more or
 * tell the user. It is also not a cancellation — a call blocked on a socket or a database read
 * cannot be killed, so the work may still finish and its output is simply discarded.</p>
 *
 * <p>Result metadata (status, result count) is read back from {@link AgentTrace}: tools already
 * record it, so the wrapper stays free of tool-specific knowledge.</p>
 */
final class StepTrackingToolCallback implements ToolCallback {

    /** Argument keys that make a readable card title, in priority order. */
    private static final List<String> ARGUMENT_KEYS =
            List.of("query", "name", "language", "kind", "content", "code", "stdin");

    private static final int MAX_ARGUMENT_CHARS = 80;

    /**
     * Deadline enforcement has to run the tool off the calling thread; virtual threads make that
     * free and keep a blocked call from occupying a platform thread for the rest of the turn.
     */
    private static final ExecutorService CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private final ToolCallback delegate;
    private final AgentRun run;
    private final AgentTrace trace;
    private final int timeoutMs;
    private final RenderPolicy render;
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * @param delegate  tool to invoke
     * @param run       turn stream that receives the step markers, or {@code null} when there is no
     *                  live channel (the non-streaming path still needs the deadline)
     * @param trace     turn trace the delegate records its call into
     * @param timeoutMs per-call deadline, or {@code 0} to defer to the tool's own budget
     * @param render    how the client presents this tool
     */
    StepTrackingToolCallback(ToolCallback delegate, AgentRun run, AgentTrace trace, int timeoutMs,
            RenderPolicy render) {
        this.delegate = delegate;
        this.run = run;
        this.trace = trace;
        this.timeoutMs = timeoutMs;
        this.render = render == null ? RenderPolicy.FULL : render;
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
        step(AiStreamChunk.Step.toolStart(tool, callId, summarise(toolInput), this.render));
        int recorded = callCount();
        long startedAt = System.nanoTime();
        String result;
        try {
            result = invoke(toolInput, toolContext);
        } catch (ToolDeadlineExceeded ex) {
            result = timeoutResponse(tool, toolInput).json();
        } catch (RuntimeException ex) {
            step(AiStreamChunk.Step.toolEnd(tool, callId, ToolCode.UNAVAILABLE.getCode(), 0,
                    elapsedMillis(startedAt), this.render));
            throw ex;
        }
        ToolCall call = lastCallFrom(recorded);
        step(AiStreamChunk.Step.toolEnd(tool, callId,
                call == null ? ToolCode.OK.getCode() : call.status(),
                call == null ? 0 : call.resultCount(),
                elapsedMillis(startedAt), this.render));
        return result;
    }

    /**
     * Runs the tool, off-thread when a deadline is configured.
     *
     * @param toolInput  raw tool input JSON
     * @param toolContext Spring AI tool context (carries the {@link AgentTrace})
     * @return the tool's JSON answer
     */
    private String invoke(String toolInput, ToolContext toolContext) {
        if (this.timeoutMs <= 0) {
            return this.delegate.call(toolInput, toolContext);
        }
        Future<String> future = CALLS.submit(() -> this.delegate.call(toolInput, toolContext));
        try {
            return future.get(this.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // The interrupt is best-effort: a blocked read usually ignores it, so the thread finishes
            // on its own and its result is dropped. That is why the model gets a retryable code
            // rather than a promise that the work stopped.
            future.cancel(true);
            throw new ToolDeadlineExceeded(this.timeoutMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IllegalStateException("tool call interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(Objects.requireNonNullElse(cause, ex).getMessage(), cause);
        }
    }

    /**
     * @param tool      tool name
     * @param toolInput raw input, used for the trace argument
     * @return the envelope the model reads, already recorded on the trace
     */
    private ToolResponse timeoutResponse(String tool, String toolInput) {
        Map<String, Object> data = Map.of("tool", tool, "timeoutMs", this.timeoutMs);
        return ToolResponse.of(ToolCode.TIMEOUT, timeoutMessage(tool), data)
                .record(this.trace, tool, summarise(toolInput), 0);
    }

    private String timeoutMessage(String tool) {
        return tool + " 在 " + budget() + "内没有返回，本次调用已放弃。可以换更小的参数重试一次，"
                + "或直接向用户说明该结果暂时取不到。";
    }

    private String budget() {
        return this.timeoutMs % 1000 == 0
                ? (this.timeoutMs / 1000) + " 秒"
                : this.timeoutMs + " 毫秒";
    }

    private void step(AiStreamChunk.Step step) {
        // EXCLUDED calls stay on the trace but never reach the live channel: an internal result the
        // answer pipeline consumed is not an action the user should watch. The settled tool list is
        // filtered with the same policy, so live and persisted views can never disagree.
        if (this.run != null && this.render != RenderPolicy.EXCLUDED) {
            this.run.step(step);
        }
    }

    private int callCount() {
        return this.trace == null ? 0 : this.trace.callCount();
    }

    private ToolCall lastCallFrom(int from) {
        return this.trace == null ? null : this.trace.lastCallFrom(from);
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

    /** Internal signal: the call outlived its deadline and was abandoned. */
    private static final class ToolDeadlineExceeded extends RuntimeException {

        private ToolDeadlineExceeded(int timeoutMs) {
            super("tool call exceeded " + timeoutMs + "ms");
        }
    }
}
