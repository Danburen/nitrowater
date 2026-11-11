package cn.nitrowater.server.service.ai.model.usage;

import org.springframework.ai.chat.metadata.Usage;

import java.util.concurrent.atomic.AtomicLong;
import cn.nitrowater.lib.utils.StringUtil;

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

    /** @return provider-reported input+output accumulated so far ({@code 0} before the first usage) */
    public synchronized long consumed() {
        return inputTokens.get() + outputTokens.get();
    }

    /**
     * Estimates provider tokens for a piece of text, without a tokenizer.
     *
     * <p>CJK characters count 1:1 — a Chinese character is about one token in the tokenizers these
     * providers use — while everything else keeps the classic four-characters-per-token rule. A flat
     * {@code (chars + 3) / 4} would under-report a Chinese conversation by a factor of ~3, and the
     * context window and the compaction trigger are expressed in these units: an estimate that is
     * off by 3x silently overflows the model window (and undercounts the daily quota).</p>
     *
     * @param text text to measure, may be {@code null}
     * @return estimated tokens, {@code 0} for blank text
     */
    public static int estimateTokens(String text) {
        return (int) Math.min(Integer.MAX_VALUE, estimate(text));
    }

    private static long estimate(String text) {
        if (StringUtil.isBlank(text)) {
            return 0;
        }
        long wide = 0;
        long narrow = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (isWide(codePoint)) {
                wide++;
            } else {
                narrow++;
            }
            index += Character.charCount(codePoint);
        }
        return Math.max(1, wide + (narrow + 3L) / 4L);
    }

    /**
     * @param codePoint unicode code point
     * @return whether the code point is a CJK character or a full-width form that is one token
     */
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x30FF)      // CJK punctuation, kana
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)  // CJK extension A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)  // CJK unified ideographs
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)  // compatibility ideographs
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF)  // full-width forms
                || (codePoint >= 0x20000 && codePoint <= 0x2FA1F); // extensions B onwards
    }
}
