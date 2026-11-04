package cn.nitrowater.server.web.dto;

/**
 * Full skill payload for the console's detail/editor view.
 *
 * @param id managed row id, {@code null} for a pure built-in
 * @param name skill name
 * @param description one-line description
 * @param whenToUse when the agent should load it
 * @param body markdown instructions
 * @param source {@code builtin} or {@code managed}
 * @param origin how a managed row was authored ({@code FORM} / {@code IMPORT}), {@code null} for a built-in
 * @param builtin whether the name exists on the classpath
 * @param overridesBuiltin a managed row shadows a built-in of the same name
 * @param updatedAt last write time, {@code null} for a pure built-in
 */
public record AiSkillDetailResp(Long id, String name, String description, String whenToUse,
        String body, String source, String origin, boolean builtin, boolean overridesBuiltin,
        java.time.Instant updatedAt) {
}
