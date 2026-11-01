package cn.nitrowater.server.ai;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Objects;

/**
 * One element of the model stream.
 *
 * <p>Exactly one payload field is set per element:</p>
 * <ul>
 *   <li>{@code text} — a visible delta (may be empty on a usage-only frame);</li>
 *   <li>{@code step} — a progress marker (tool started/finished, next round thinking), rendered as
 *       a live activity card and never persisted;</li>
 *   <li>{@code usage} / {@code finishReason} — provider metadata attached to a frame.</li>
 * </ul>
 */
public record AiStreamChunk(String text, Usage usage, String finishReason, Step step) {

    /**
     * A progress marker for the live activity card.
     *
     * @param phase {@code tool_start}, {@code tool_end} or {@code thinking}
     * @param tool tool name, or {@code null} for {@code thinking}
     */
    public record Step(String phase, String tool) {
    }

    /** Converts a Spring AI response without exposing provider response types to web controllers. */
    public static AiStreamChunk from(ChatResponse response) {
        if (response == null) {
            return new AiStreamChunk("", null, null, null);
        }
        response.getMetadata();
        Usage usage = response.getMetadata().getUsage();
        if (response.getResult() == null) {
            return new AiStreamChunk("", usage, null, null);
        } else {
            response.getResult().getOutput();
        }
        response.getResult().getMetadata();
        String finishReason = response.getResult().getMetadata().getFinishReason();
        return new AiStreamChunk(
                Objects.requireNonNullElse(response.getResult().getOutput().getText(), ""),
                usage, finishReason, null);
    }

    /**
     * @param phase {@code tool_start}, {@code tool_end} or {@code thinking}
     * @param tool tool name, or {@code null} for {@code thinking}
     * @return a progress-only chunk
     */
    public static AiStreamChunk step(String phase, String tool) {
        return new AiStreamChunk("", null, null, new Step(phase, tool));
    }
}
