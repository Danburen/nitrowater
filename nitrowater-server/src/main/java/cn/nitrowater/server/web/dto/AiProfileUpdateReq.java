package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.model.memory.AiProfileUpdate;
import cn.nitrowater.server.infra.enums.Expertise;
import cn.nitrowater.server.infra.enums.ReplyStyle;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update for the authenticated user's NitroBot profile. {@code null} fields are left
 * unchanged; an empty {@code preferences} list clears the free-form preferences.
 *
 * @param language preferred answer language
 * @param replyStyle answer length preference
 * @param expertise self-declared experience level
 * @param timezone IANA timezone, or blank to clear
 * @param preferences free-form preference lines
 * @param inferenceOptIn whether automatic inference is allowed
 */
public record AiProfileUpdateReq(
        @Size(max = 16, message = "language must be at most 16 characters")
        String language,
        ReplyStyle replyStyle,
        Expertise expertise,
        @Size(max = 64, message = "timezone must be at most 64 characters")
        String timezone,
        @Size(max = 20, message = "at most 20 preferences are allowed")
        List<@Size(max = 200, message = "each preference must be at most 200 characters") String> preferences,
        Boolean inferenceOptIn) {

    /** @return the service update built from this validated body */
    public AiProfileUpdate toUpdate() {
        return new AiProfileUpdate(language, replyStyle, expertise, timezone, preferences,
                inferenceOptIn);
    }
}
