package cn.nitrowater.server.service.ai.model.skill;

/**
 * Create/update payload of a managed skill, detached from the HTTP DTO.
 *
 * @param name stable identifier (lowercase kebab-case); immutable on update
 * @param description one-line catalog description
 * @param whenToUse when the agent should load it
 * @param body markdown instructions
 */
public record SkillDraft(String name, String description, String whenToUse, String body) {
}
