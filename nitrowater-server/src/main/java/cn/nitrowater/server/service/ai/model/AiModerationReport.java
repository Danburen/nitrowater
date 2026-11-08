package cn.nitrowater.server.service.ai.model;

import org.springframework.data.domain.Page;

/**
 * Moderation overview returned by the admin console.
 *
 * @param mode active posture ({@code OFF} / {@code LOG} / {@code ENFORCE})
 * @param dictionarySize number of violation words currently loaded
 * @param hitsLast24Hours hits recorded in the last 24 hours (all directions)
 * @param hits the paged hits, newest first
 */
public record AiModerationReport(String mode, int dictionarySize, long hitsLast24Hours,
        Page<AiModerationHitView> hits) {
}
