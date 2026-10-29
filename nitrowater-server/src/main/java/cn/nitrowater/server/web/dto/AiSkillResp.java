package cn.nitrowater.server.web.dto;

/**
 * A skill shown in the chat skill picker (and the admin console).
 *
 * @param name skill name
 * @param description one-line description
 * @param whenToUse when the agent should load it
 * @param source {@code builtin} or {@code external}
 */
public record AiSkillResp(String name, String description, String whenToUse, String source) {
}
