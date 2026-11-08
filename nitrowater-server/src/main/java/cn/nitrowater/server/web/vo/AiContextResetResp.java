package cn.nitrowater.server.web.vo;

/**
 * Result of {@code POST /api/ai/slash/clear}.
 *
 * <p>Three plain scalars, so the controller builds it directly instead of routing through a
 * factory.</p>
 *
 * @param cleared always {@code true} when the call returns
 * @param droppedMessages how many turns left the model's context
 * @param boundarySeq highest seq excluded from the context ({@code 0} for an empty conversation)
 */
public record AiContextResetResp(boolean cleared, int droppedMessages, int boundarySeq) {
}
