package cn.nitrowater.server.service.ai.model.agent;

/**
 * Outcome of a context reset ({@code /clear}).
 *
 * @param cleared always {@code true} when the call returns
 * @param droppedMessages how many turns were removed from the model's context
 * @param boundarySeq highest seq excluded from the context ({@code 0} for an empty conversation)
 */
public record ContextReset(boolean cleared, int droppedMessages, int boundarySeq) {
}
