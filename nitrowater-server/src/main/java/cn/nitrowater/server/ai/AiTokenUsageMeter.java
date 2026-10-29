package cn.nitrowater.server.ai;

import org.springframework.ai.chat.metadata.Usage;

import java.util.concurrent.atomic.AtomicLong;

/** Collects usage metadata emitted by one or more model calls in a Spring AI stream. */
public final class AiTokenUsageMeter {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private boolean reported;

    /** Adds one provider-reported usage record, ignoring empty streaming chunks. */
    public synchronized void record(Usage usage) {
        if (usage == null) {
            return;
        }
        Integer input = usage.getPromptTokens();
        Integer output = usage.getCompletionTokens();
        if ((input == null || input == 0) && (output == null || output == 0)) {
            return;
        }
        inputTokens.addAndGet(Math.max(0, input == null ? 0 : input));
        outputTokens.addAndGet(Math.max(0, output == null ? 0 : output));
        reported = true;
    }

    /** @param estimatedInput fallback prompt token estimate
     *  @param output fallback generated text
     *  @return the provider usage total when available, otherwise a marked estimate */
    public synchronized AiTokenUsage snapshot(int estimatedInput, String output) {
        if (reported) {
            return new AiTokenUsage(inputTokens.get(), outputTokens.get(), false);
        }
        return new AiTokenUsage(Math.max(0, estimatedInput), estimate(output), true);
    }

    /** @return whether the provider supplied any usage metadata */
    public synchronized boolean hasReportedUsage() {
        return reported;
    }

    /** Estimates provider tokens with the same conservative character fallback used by persistence. */
    public static int estimateTokens(String text) {
        return (int) Math.min(Integer.MAX_VALUE, estimate(text));
    }

    private static long estimate(String text) {
        return text == null || text.isBlank() ? 0 : Math.max(1, (text.length() + 3L) / 4L);
    }
}
