package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Rename / archive request; both fields are optional and only the non-null ones are applied.
 *
 * @param title new title
 * @param archived {@code true} archives, {@code false} restores
 */
public record ConversationPatchReq(
        @Size(max = 120, message = "title must be at most 120 characters")
        String title,
        Boolean archived) {
}
