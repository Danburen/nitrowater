package cn.nitrowater.server.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Chat request.
 *
 * @param question the user question (blank questions are rejected)
 * @param conversationUuid server-side conversation to continue; blank starts a new one
 * @param history optional previous turns (legacy clients without server-side history)
 */
public record AiChatReq(
        @NotBlank(message = "question is required")
        @Size(max = 4000, message = "question must be at most 4000 characters")
        String question,
        String conversationUuid,
        List<@Valid AiChatTurn> history) {
}
