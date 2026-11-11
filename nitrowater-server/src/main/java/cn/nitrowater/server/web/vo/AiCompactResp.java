package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.agent.CompactResult;

/**
 * Result of {@code POST /api/ai/conversations/{uuid}/compact}.
 *
 * @param compacted whether the summary and the boundary were written
 * @param summarizedMessages messages folded into the summary by this call
 * @param boundarySeq new context boundary
 * @param summaryChars length of the stored summary
 * @param remainingMessages messages still replayed verbatim
 * @param reason machine-readable explanation ({@code ok} on success)
 */
public record AiCompactResp(
        boolean compacted,
        int summarizedMessages,
        int boundarySeq,
        int summaryChars,
        int remainingMessages,
        String reason
) {

    /**
     * @param result service outcome
     * @return the wire representation
     */
    public static AiCompactResp from(CompactResult result) {
        return new AiCompactResp(result.compacted(), result.summarizedMessages(),
                result.boundarySeq(), result.summaryChars(), result.remainingMessages(),
                result.reason());
    }
}
