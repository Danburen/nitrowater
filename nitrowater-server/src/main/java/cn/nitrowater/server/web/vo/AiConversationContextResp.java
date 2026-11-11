package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.agent.ConversationContext;

/**
 * Result of {@code GET /api/ai/conversations/{uuid}/context}.
 *
 * @param boundarySeq highest message seq excluded from the prompt
 * @param summary rolling summary of the excluded turns, or {@code null}
 * @param summarizedMessages messages at or below the boundary
 * @param pendingMessages messages above the boundary (currently replayed)
 * @param compactTriggerMessages unsummarized messages that trigger an automatic compaction
 * @param keepMessages messages kept verbatim after a compaction
 */
public record AiConversationContextResp(
        int boundarySeq,
        String summary,
        int summarizedMessages,
        int pendingMessages,
        int compactTriggerMessages,
        int keepMessages
) {

    /**
     * @param context service view
     * @return the wire representation
     */
    public static AiConversationContextResp from(ConversationContext context) {
        return new AiConversationContextResp(context.boundarySeq(), context.summary(),
                context.summarizedMessages(), context.pendingMessages(),
                context.compactTriggerMessages(), context.keepMessages());
    }
}
