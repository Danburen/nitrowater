package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.entity.AiSkillEntity;
import cn.nitrowater.server.infra.enums.SkillOrigin;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.model.Skill;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies built-in discovery, the managed override rule and front-matter parsing. */
class SkillRegistryTest {

    private final AiSkillRepository rows = mock(AiSkillRepository.class);
    private final SkillRegistry registry = new SkillRegistry(rows);

    @Test
    void findsBuiltInSkills() {
        when(rows.findAllByOrderByNameAsc()).thenReturn(List.of());

        assertThat(registry.list()).extracting(Skill::name)
                .contains("config-walkthrough", "troubleshooting", "command-reference");
        assertThat(registry.get("config-walkthrough")).isNotNull();
        assertThat(registry.get("CONFIG-WALKTHROUGH")).isNotNull();
        assertThat(registry.get("missing")).isNull();
        assertThat(registry.get("config-walkthrough").source()).isEqualTo(SkillRegistry.SOURCE_BUILTIN);
    }

    @Test
    void managedRowOverridesBuiltIn() {
        when(rows.findAllByOrderByNameAsc()).thenReturn(List.of(row("config-walkthrough",
                "managed version", "managed body")));

        Skill skill = registry.get("config-walkthrough");

        assertThat(skill).isNotNull();
        assertThat(skill.source()).isEqualTo(SkillRegistry.SOURCE_MANAGED);
        assertThat(skill.description()).isEqualTo("managed version");
        assertThat(skill.body()).isEqualTo("managed body");
        assertThat(registry.builtinNames()).contains("config-walkthrough");
    }

    @Test
    void parsesFrontMatterWithoutQuotes() {
        Skill skill = SkillRegistry.parse("""
                ---
                name: demo
                description: "带引号描述"
                when_to_use: '适用场景'
                ---
                正文
                """, "fallback", SkillRegistry.SOURCE_MANAGED);

        assertThat(skill.name()).isEqualTo("demo");
        assertThat(skill.description()).isEqualTo("带引号描述");
        assertThat(skill.whenToUse()).isEqualTo("适用场景");
        assertThat(skill.body()).isEqualTo("正文");
    }

    private static AiSkillEntity row(String name, String description, String body) {
        AiSkillEntity entity = new AiSkillEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setWhenToUse("");
        entity.setBody(body);
        entity.setOrigin(SkillOrigin.FORM);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
