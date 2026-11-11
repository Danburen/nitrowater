package cn.nitrowater.server.service.ai.agent;

import cn.nitrowater.server.service.ai.model.agent.RenderPolicy;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Objects;

/**
 * One element of the agent turn's outbound stream.
 *
 * <p>Exactly one payload field is set per element:</p>
 * <ul>
 *   <li>{@code text} — a visible answer delta (may be empty on a usage-only frame);</li>
 *   <li>{@code step} — a progress marker for the live step list (thinking, tool started/finished).
 *       Steps are never persisted and never contain answer text;</li>
 *   <li>{@code usage} / {@code finishReason} — provider metadata attached to a frame.</li>
 * </ul>
 *
 * <p>Process and answer are deliberately separate channels: the browser renders steps as an
 * independent timeline and only starts the answer bubble from {@code text}, so no prompt-side
 * narration is needed to explain what the agent is doing.</p>
 */
public record AiStreamChunk(String text, Usage usage, String finishReason, Step step) {

    /**
     * A progress marker of one agent turn.
     *
     * @param phase       {@code thinking}, {@code tool_start} or {@code tool_end}
     * @param tool        tool name; {@code null} for {@code thinking}
     * @param callId      per-invocation id so a start can be paired with its end; {@code null} for
     *                    {@code thinking}
     * @param argument    human-readable argument summary, set on {@code tool_start}
     * @param status      outcome code from {@link ToolCode#getCode()}, set on {@code tool_end}
     * @param ok          whether the call was handled (drives the badge, so the client never has to
     *                    compare code strings), set on {@code tool_end}
     * @param resultCount results produced, set on {@code tool_end}
     * @param durationMs  wall-clock duration of the invocation, set on {@code tool_end}
     * @param render      how the client presents the call; transport metadata only, so it changes
     *                    neither the model-facing schema nor the trace
     */
    public record Step(String phase, String tool, String callId, String argument,
            String status, Boolean ok, Integer resultCount, Long durationMs, RenderPolicy render) {

        /** @return the marker emitted at the start of a round, before any model token */
        public static Step thinking() {
            return new Step("thinking", null, null, null, null, null, null, null, RenderPolicy.FULL);
        }

        /**
         * @param tool     tool name
         * @param callId   per-invocation id
         * @param argument argument summary, or {@code null}
         * @return the marker emitted right before the tool runs
         */
        public static Step toolStart(String tool, String callId, String argument) {
            return toolStart(tool, callId, argument, RenderPolicy.FULL);
        }

        /**
         * @param tool     tool name
         * @param callId   per-invocation id
         * @param argument argument summary, or {@code null}
         * @param render   how the client presents the call
         * @return the marker emitted right before the tool runs
         */
        public static Step toolStart(String tool, String callId, String argument,
                RenderPolicy render) {
            return new Step("tool_start", tool, callId, argument, null, null, null, null, render);
        }

        /**
         * @param tool        tool name
         * @param callId      per-invocation id matching the start marker
         * @param status      outcome code
         * @param resultCount results produced
         * @param durationMs  wall-clock duration
         * @return the marker emitted right after the tool returned
         */
        public static Step toolEnd(String tool, String callId, String status, int resultCount,
                long durationMs) {
            return toolEnd(tool, callId, status, resultCount, durationMs, RenderPolicy.FULL);
        }

        /**
         * @param tool        tool name
         * @param callId      per-invocation id matching the start marker
         * @param status      outcome code
         * @param resultCount results produced
         * @param durationMs  wall-clock duration
         * @param render      how the client presents the call
         * @return the marker emitted right after the tool returned
         */
        public static Step toolEnd(String tool, String callId, String status, int resultCount,
                long durationMs, RenderPolicy render) {
            return new Step("tool_end", tool, callId, null, status,
                    ToolCode.isSuccessCode(status), resultCount, durationMs, render);
        }
    }

    /** Converts a Spring AI response without exposing provider response types to web controllers. */
    public static AiStreamChunk from(ChatResponse response) {
        if (response == null) {
            return new AiStreamChunk("", null, null, null);
        }
        Usage usage = response.getMetadata().getUsage();
        if (response.getResult() == null) {
            return new AiStreamChunk("", usage, null, null);
        }
        return new AiStreamChunk(
                Objects.requireNonNullElse(response.getResult().getOutput().getText(), ""),
                usage, response.getResult().getMetadata().getFinishReason(), null);
    }
}
