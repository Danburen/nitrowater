package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.entity.AiSkillEntity;
import cn.nitrowater.server.infra.enums.SkillOrigin;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.model.skill.SkillExport;
import cn.nitrowater.server.service.ai.skill.SkillRegistry;
import cn.nitrowater.server.service.ai.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the markdown import fallback and the export round-trip. */
class SkillServiceTest {

    private final AiSkillRepository rows = mock(AiSkillRepository.class);
    private final SkillRegistry registry = mock(SkillRegistry.class);
    private final SkillService service = new SkillService(rows, registry);

    @Test
    void importFallsBackToTheFirstBodyLineAsDescription() throws Exception {
        when(rows.findByNameIgnoreCase("demo")).thenReturn(Optional.empty());
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registry.get("demo")).thenReturn(
                new Skill("demo", "", "", "# Demo skill\n\nDo the thing", SkillRegistry.SOURCE_MANAGED));
        when(registry.builtinNames()).thenReturn(Set.of());

        MockMultipartFile file = new MockMultipartFile("file", "demo.md", "text/markdown",
                "# Demo skill\n\nDo the thing\n".getBytes(StandardCharsets.UTF_8));
        service.importMarkdown(file.getBytes(), file.getOriginalFilename(), 7L);

        ArgumentCaptor<AiSkillEntity> saved = ArgumentCaptor.forClass(AiSkillEntity.class);
        verify(rows).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("Demo skill");
        assertThat(saved.getValue().getOrigin()).isEqualTo(SkillOrigin.IMPORT);
    }

    @Test
    void exportRendersFrontMatterThatParsesBackToTheSameSkill() {
        Skill skill = new Skill("demo", "A demo", "Use it", "Do the thing",
                SkillRegistry.SOURCE_MANAGED);
        when(registry.get("demo")).thenReturn(skill);
        when(registry.builtinNames()).thenReturn(Set.of());
        when(rows.findByNameIgnoreCase("demo")).thenReturn(Optional.of(row()));

        SkillExport exported = service.exportMarkdown("demo");

        assertThat(exported.fileName()).isEqualTo("demo.md");
        assertThat(exported.content()).startsWith("---\nname: demo\n");
        Skill reparsed = SkillRegistry.parse(exported.content(), "fallback",
                SkillRegistry.SOURCE_MANAGED);
        assertThat(reparsed.name()).isEqualTo("demo");
        assertThat(reparsed.description()).isEqualTo("A demo");
        assertThat(reparsed.whenToUse()).isEqualTo("Use it");
        assertThat(reparsed.body()).isEqualTo("Do the thing");
    }

    private static AiSkillEntity row() {
        AiSkillEntity entity = new AiSkillEntity();
        entity.setId(1L);
        entity.setName("demo");
        entity.setDescription("A demo");
        entity.setWhenToUse("Use it");
        entity.setBody("Do the thing");
        entity.setOrigin(SkillOrigin.FORM);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
