package cn.nitrowater.server.web.dto;

import java.util.List;

/**
 * Content-moderation overview for the admin console.
 *
 * @param mode active posture ({@code OFF} / {@code LOG} / {@code ENFORCE})
 * @param dictionarySize number of violation words currently loaded
 * @param hitsLast24Hours hits recorded in the last 24 hours (all directions)
 * @param total hits matching the active filters, ignoring the paging cursor
 * @param hasMore whether older hits exist beyond this page
 * @param hits the page, newest first
 */
public record AiModerationResp(String mode, int dictionarySize, long hitsLast24Hours, long total,
        boolean hasMore, List<AiModerationHitResp> hits) {
}
