package cn.nitrowater.server.service.ai.model;

import java.util.List;

/**
 * One agent turn as submitted by a client, already detached from the HTTP DTO.
 *
 * <p>Built by the controller from the validated request body so the service layer never imports
 * web types. The conversation uuid is blank on a first turn; {@code continueGeneration} turns reuse
 * the stored transcript and ignore {@code question}.</p>
 *
 * @param question              sanitized-by-the-service question text
 * @param conversationUuid      conversation to continue, or {@code null} to start a new one
 * @param history               previous turns supplied by legacy clients that start without a uuid
 * @param skill                 optional selected skill name
 * @param allowWeb              whether the user opted into web search for this turn
 * @param continueGeneration    continue a truncated answer instead of asking a new question
 */
public record AgentTurnCommand(
        String question,
        String conversationUuid,
        List<ChatTurn> history,
        String skill,
        boolean allowWeb,
        boolean continueGeneration
) {
    public AgentTurnCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
