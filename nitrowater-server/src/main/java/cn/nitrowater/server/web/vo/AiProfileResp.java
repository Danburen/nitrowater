package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.AiProfile;

import java.time.Instant;
import java.util.List;

/**
 * NitroBot profile returned by {@code /api/ai/profile} and {@code /api/ai/slash/profile}.
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
public record AiProfileResp(boolean exists, String language, String replyStyle, String expertise,
        String timezone, List<String> preferences, boolean inferenceOptIn, Instant updatedAt) {

    /**
     * @param profile service view
     * @return the wire view
     */
    public static AiProfileResp from(AiProfile profile) {
        return new AiProfileResp(profile.exists(), profile.language(), profile.replyStyle(),
                profile.expertise(), profile.timezone(), profile.preferences(),
                profile.inferenceOptIn(), profile.updatedAt());
    }
}
