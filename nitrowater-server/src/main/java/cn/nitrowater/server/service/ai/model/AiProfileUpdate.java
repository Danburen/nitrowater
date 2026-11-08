package cn.nitrowater.server.service.ai.model;

import cn.nitrowater.server.infra.enums.Expertise;
import cn.nitrowater.server.infra.enums.ReplyStyle;

import java.util.List;

/**
 * Partial profile update.
 *
 * @param language preferred answer language
 * @param replyStyle answer length preference
 * @param expertise self-declared experience level
 * @param timezone IANA timezone, or blank to clear
 * @param preferences free-form preference lines; empty clears
 * @param inferenceOptIn whether automatic inference is allowed
 */
public record AiProfileUpdate(String language, ReplyStyle replyStyle, Expertise expertise,
        String timezone, List<String> preferences, Boolean inferenceOptIn) {
}
