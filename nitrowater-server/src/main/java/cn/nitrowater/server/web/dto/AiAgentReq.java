package cn.nitrowater.server.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Agent chat request.
 *
 * @param question user question
 * @param conversationUuid server-side conversation to continue; blank starts a new one
 * @param history previous turns (used by legacy clients that start without a uuid)
 * @param skill optional selected skill name (blank = automatic)
 * @param allowWeb whether the user opted into web search for this turn (only honoured when the
 *        server has a web-search provider enabled)
 * @param continueGeneration continue a truncated answer instead of asking a new question; the
 *        conversation uuid is required and no user message is appended
 */
public record AiAgentReq(
        @NotBlank(message = "question is required")
        @Size(max = 4000, message = "question must be at most 4000 characters")
        String question,
        String conversationUuid,
        List<@Valid AiChatTurn> history,
        String skill,
        boolean allowWeb,
        boolean continueGeneration) {
}
