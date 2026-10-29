package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Create-conversation request.
 *
 * @param title optional title; when blank the title is derived from the first question
 */
public record ConversationCreateReq(
        @Size(max = 120, message = "title must be at most 120 characters")
        String title) {
}
