package cn.nitrowater.server.service.ai.model.memory;

import cn.nitrowater.server.infra.enums.Expertise;
import cn.nitrowater.server.infra.enums.ReplyStyle;

import java.time.Instant;
import java.util.List;

/**
 * Profile view returned by the API and the {@code /profile} slash card.
 *
 * @param exists whether a row exists
 * @param language preferred answer language
 * @param replyStyle {@code CONCISE} / {@code BALANCED} / {@code DETAILED}
 * @param expertise {@code BEGINNER} / {@code INTERMEDIATE} / {@code EXPERT}
 * @param timezone IANA timezone, if set
 * @param preferences flattened free-form preferences
 * @param inferenceOptIn whether automatic inference is allowed
 * @param updatedAt last change, or {@code null} when no row exists
 */
public record AiProfile(boolean exists, String language, String replyStyle, String expertise,
        String timezone, List<String> preferences, boolean inferenceOptIn, Instant updatedAt) {

    /**
     * @return the defaults-only snapshot used when the user has no stored row
     */
    public static AiProfile defaults() {
        return new AiProfile(false, "zh-CN", ReplyStyle.BALANCED.name(), Expertise.INTERMEDIATE.name(),
                null, List.of(), false, null);
    }
}
