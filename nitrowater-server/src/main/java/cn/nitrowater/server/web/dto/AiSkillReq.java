package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.ai.model.skill.SkillDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a managed skill.
 *
 * @param name stable identifier (lowercase kebab-case); immutable on update
 * @param description one-line catalog description
 * @param whenToUse when the agent should load it
 * @param body markdown instructions
 */
public record AiSkillReq(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$",
                message = "name must be lowercase letters, digits and dashes (max 64)")
        String name,

        @Size(max = 255)
        String description,

        @Size(max = 255)
        String whenToUse,

        @NotBlank
        @Size(max = 20000)
        String body) {

    /** @return the service draft built from this validated body */
    public SkillDraft toDraft() {
        return new SkillDraft(name, description, whenToUse, body);
    }
}
