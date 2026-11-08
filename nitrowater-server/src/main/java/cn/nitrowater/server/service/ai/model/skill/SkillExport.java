package cn.nitrowater.server.service.ai.model.skill;

/**
 * Rendered {@code SKILL.md} download.
 *
 * @param fileName suggested download name
 * @param content UTF-8 markdown (front matter + body)
 */
public record SkillExport(String fileName, String content) {
}
