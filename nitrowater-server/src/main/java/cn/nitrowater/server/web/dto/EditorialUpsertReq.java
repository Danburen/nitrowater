package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for an editorial (admin only).
 *
 * @param title title (required)
 * @param content Chinese markdown body (required)
 * @param language code-snippet language id, e.g. {@code java} (optional)
 * @param status {@code DRAFT}/{@code PUBLISHED}/{@code HIDDEN} (default {@code DRAFT})
 * @param pinned whether pinned (optional)
 */
public record EditorialUpsertReq(
        @NotBlank String title,
        @NotBlank String content,
        String language,
        String status,
        Boolean pinned) {
}
