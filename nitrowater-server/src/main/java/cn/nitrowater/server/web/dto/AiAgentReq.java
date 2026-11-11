package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.model.agent.AgentTurnCommand;
import cn.nitrowater.server.service.ai.model.agent.ChatTurn;
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
 * @param skill deprecated single-skill field, still honoured when {@code skills} is absent
 * @param skills explicitly selected skill names, in priority order (empty = automatic)
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
        @Size(max = 8, message = "skills must contain at most 8 entries")
        List<@Size(max = 64, message = "a skill name must be at most 64 characters") String> skills,
        boolean allowWeb,
        boolean continueGeneration
) {
    /** @return the service command built from this validated body */
    public AgentTurnCommand toCommand() {
        List<ChatTurn> turns;
        if (history == null) {
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
                selection(),
                allowWeb,
                continueGeneration
        );
    }

    /**
     * The new {@code skills} list wins; a legacy caller still sending the single {@code skill}
     * keeps working instead of silently losing its selection.
     *
     * @return the selected skill names, never {@code null}
     */
    private List<String> selection() {
        if (skills != null && !skills.isEmpty()) {
            return skills;
        }
        if (skill == null || skill.isBlank()) {
            return List.of();
        }
        return List.of(skill);
    }
}
