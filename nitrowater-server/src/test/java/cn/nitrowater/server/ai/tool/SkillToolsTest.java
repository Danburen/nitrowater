package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.skill.SkillRegistry;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.tool.SkillTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Verifies the skill tool answers in the shared envelope and records a real outcome. */
class SkillToolsTest {

    private final SkillRegistry registry = new SkillRegistry(mock(AiSkillRepository.class));

    @Test
    void unknownSkillListsTheCatalogInsteadOfAnsweringBlank() {
        AgentTrace trace = new AgentTrace();

        String json = new SkillTools(registry).loadSkill("no-such-skill", context(trace));

        assertThat(json).contains("\"success\":false")
                .contains("\"code\":\"tool.invalid_request\"")
                .contains("\"available\"");
        assertThat(trace.calls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("loadSkill");
            assertThat(call.ok()).isFalse();
        });
    }

    @Test
    void loadsAKnownSkillAndRecordsItOnTheTrace() {
        Skill skill = registry.list().getFirst();
        AgentTrace trace = new AgentTrace();

        String json = new SkillTools(registry).loadSkill(skill.name(), context(trace));

        assertThat(json).contains("\"code\":\"tool.ok\"").contains("\"instructions\"");
        assertThat(trace.skills()).containsExactly(skill.name());
        assertThat(trace.calls()).singleElement()
                .satisfies(call -> assertThat(call.ok()).isTrue());
    }

    private static ToolContext context(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));
    }
}
