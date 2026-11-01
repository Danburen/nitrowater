package cn.nitrowater.server.ai.skill;

import cn.nitrowater.server.ai.properties.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies built-in discovery, front-matter parsing and external overrides. */
class SkillRegistryTest {

    @TempDir
    Path skillsDir;

    @Test
    void findsBuiltInSkills() {
        SkillRegistry registry = new SkillRegistry(properties());

        assertThat(registry.list()).extracting(Skill::name)
                .contains("config-walkthrough", "troubleshooting", "command-reference");
        assertThat(registry.get("config-walkthrough")).isNotNull();
        assertThat(registry.get("CONFIG-WALKTHROUGH")).isNotNull();
        assertThat(registry.get("missing")).isNull();
    }

    @Test
    void externalSkillOverridesBuiltIn() throws IOException {
        Path dir = skillsDir.resolve("config-walkthrough");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), """
                ---
                name: config-walkthrough
                description: 外部覆盖版本
                when_to_use: 测试
                ---
                外部工作流正文。
                """, StandardCharsets.UTF_8);

        SkillRegistry registry = new SkillRegistry(properties());

        Skill skill = registry.get("config-walkthrough");
        assertThat(skill).isNotNull();
        assertThat(skill.source()).isEqualTo("external");
        assertThat(skill.description()).isEqualTo("外部覆盖版本");
        assertThat(skill.body()).contains("外部工作流正文");
    }

    @Test
    void parsesFrontMatterWithoutQuotes() {
        Skill skill = SkillRegistry.parse("""
                ---
                name: demo
                description: "带引号的描述"
                when_to_use: '单引号'
                ---
                正文
                """, "fallback", "builtin");

        assertThat(skill.name()).isEqualTo("demo");
        assertThat(skill.description()).isEqualTo("带引号的描述");
        assertThat(skill.whenToUse()).isEqualTo("单引号");
        assertThat(skill.body()).isEqualTo("正文");
    }

    private AiProperties properties() {
        return new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                skillsDir.toString(), java.util.List.of());
    }
}
