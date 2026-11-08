package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.model.AgentTurnCommand;
import cn.nitrowater.server.service.ai.model.ChatTurn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
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
        boolean continueGeneration
) {
    /** @return the service command built from this validated body */
    public AgentTurnCommand toCommand() {
        List<ChatTurn> turns;
        if(history == null){
            turns = new ArrayList<>();
        } else {
            turns = history.stream()
                    .map(turn -> new ChatTurn(turn.role(), turn.content()))
                    .toList();
        }
        return new AgentTurnCommand(
                question,
                conversationUuid,
                turns,
                skill,
                allowWeb,
                continueGeneration
        );
    }
}
