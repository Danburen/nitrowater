package cn.nitrowater.server.service.ai.model.agent;

/**
 * What the model currently sees of one conversation.
 *
 * <p>Messages at or below {@link #boundarySeq} are no longer replayed: they were folded into
 * {@link #summary} by a compaction or dropped by {@code /clear}. The two counts make that visible
 * to the user, and {@link #compactTriggerMessages} explains when the next automatic compaction
 * will run.</p>
 *
 * @param boundarySeq highest message seq excluded from the prompt ({@code 0} when nothing is)
 * @param summary rolling summary of the excluded turns, or {@code null}
 * @param summarizedMessages messages at or below the boundary
 * @param pendingMessages messages above the boundary (currently replayed)
 * @param compactTriggerMessages unsummarized messages that trigger an automatic compaction
 * @param keepMessages messages kept verbatim after a compaction
 */
public record ConversationContext(
        int boundarySeq,
        String summary,
        int summarizedMessages,
        int pendingMessages,
        int compactTriggerMessages,
        int keepMessages
) {
}
