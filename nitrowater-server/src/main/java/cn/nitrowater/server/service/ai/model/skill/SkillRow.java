package cn.nitrowater.server.service.ai.model.skill;

import java.time.Instant;

/**
 * One catalog row (no body: the list stays light).
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
public record SkillRow(Long id, String name, String description, String whenToUse, String source,
        boolean builtin, boolean overridesBuiltin, Instant updatedAt) {
}
