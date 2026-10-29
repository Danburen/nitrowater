package cn.nitrowater.server.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/ai/slash/clear}.
 *
 * @param conversationUuid conversation whose model context should be dropped
 */
public record AiSlashClearReq(@NotBlank String conversationUuid) {
}
