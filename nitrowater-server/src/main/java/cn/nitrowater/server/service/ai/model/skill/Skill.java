package cn.nitrowater.server.service.ai.model.skill;

/**
 * A reusable agent workflow loaded from a {@code SKILL.md} file.
 *
 * <p>Skills are instructions, not data: the agent either gets one injected by the user's selection
 * or loads one through the {@code loadSkill} tool. The body is appended to the system prompt.</p>
 *
 * @param name stable skill name (front matter {@code name}, else the directory name)
 * @param description one-line description shown to the model and the UI
 * @param whenToUse when the agent should pick this skill
 * @param body the skill instructions
 * @param source {@code builtin} (classpath) or {@code external} (skills directory)
 */
public record Skill(String name, String description, String whenToUse, String body, String source) {
}
