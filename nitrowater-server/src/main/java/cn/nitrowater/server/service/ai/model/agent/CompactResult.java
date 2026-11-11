package cn.nitrowater.server.service.ai.model.agent;

/**
 * Outcome of one compaction (manual {@code /compact} or the automatic end-of-turn pass).
 *
 * @param compacted             whether the summary and the boundary were actually written
 * @param summarizedMessages    messages folded into the summary by this call
 * @param boundarySeq           new context boundary ({@code 0} when nothing happened)
 * @param summaryChars          length of the stored summary
 * @param remainingMessages     messages still replayed verbatim
 * @param reason machine-readable explanation, {@code ok} on success
 */
public record CompactResult(
        boolean compacted,
        int summarizedMessages,
        int boundarySeq,
        int summaryChars,
        int remainingMessages,
        String reason
) {

    /**
     * @param reason why nothing was compacted
     * @param remainingMessages messages still replayed verbatim
     * @return a no-op result
     */
    public static CompactResult skipped(String reason, int remainingMessages) {
        return new CompactResult(false, 0, 0, 0, remainingMessages, reason);
    }
}
