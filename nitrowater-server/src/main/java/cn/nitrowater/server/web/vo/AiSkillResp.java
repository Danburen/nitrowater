package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.skill.SkillRegistry;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.model.skill.SkillRow;

import java.time.Instant;

/**
 * One row of the admin skill catalog (no body: the list stays light).
 *
 * @param id managed row id, {@code null} for a pure built-in
 * @param name skill name
 * @param description one-line description
 * @param whenToUse when the agent should load it
 * @param source {@code builtin} or {@code managed}
 * @param builtin whether the name exists on the classpath
 * @param overridesBuiltin a managed row shadows a built-in of the same name
 * @param updatedAt last write time, {@code null} for a pure built-in
 */
public record AiSkillResp(Long id, String name, String description, String whenToUse, String source,
        boolean builtin, boolean overridesBuiltin, Instant updatedAt) {

    /**
     * @param skill service row
     * @return the wire view
     */
    public static AiSkillResp from(SkillRow skill) {
        return new AiSkillResp(skill.id(), skill.name(), skill.description(), skill.whenToUse(),
                skill.source(), skill.builtin(), skill.overridesBuiltin(), skill.updatedAt());
    }

    /**
     * Chat-picker view of a loaded skill: picker entries carry no managed-row identity.
     *
     * @param skill loaded skill
     * @return the wire view
     */
    public static AiSkillResp fromPicker(Skill skill) {
        return new AiSkillResp(null, skill.name(), skill.description(), skill.whenToUse(),
                skill.source(), SkillRegistry.SOURCE_BUILTIN.equals(skill.source()), false, null);
    }
}
