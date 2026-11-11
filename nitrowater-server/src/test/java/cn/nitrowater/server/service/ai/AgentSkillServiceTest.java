package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.exception.SkillUnknownException;
import cn.nitrowater.server.infra.exception.SkillsDisabledException;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.skill.AgentSkillService;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the turn-time skill policy: catalog gating, multi-selection and coded rejections. */
class AgentSkillServiceTest {

    private final AiSkillRepository rows = mock(AiSkillRepository.class);
    private final AgentSkillService service = new AgentSkillService(new SkillRegistry(rows));

    public AgentSkillServiceTest() {
        when(rows.findAllByOrderByNameAsc()).thenReturn(List.of());
    }

    @Test
    void resolvesEverySelectedSkillInSelectionOrder() {
        List<Skill> selected = service.resolveSelected(
                List.of("command-reference", "CONFIG-WALKTHROUGH", " troubleshooting "), enabled());

        assertThat(selected).extracting(Skill::name)
                .containsExactly("command-reference", "config-walkthrough", "troubleshooting");
    }

    @Test
    void keepsTheFirstSpellingOfADuplicate() {
        List<Skill> selected = service.resolveSelected(
                List.of("config-walkthrough", "CONFIG-WALKTHROUGH", "config-walkthrough"), enabled());

        assertThat(selected).extracting(Skill::name).containsExactly("config-walkthrough");
    }

    @Test
    void anEmptySelectionMeansTheAgentChooses() {
        assertThat(service.resolveSelected(List.of(), enabled())).isEmpty();
        assertThat(service.resolveSelected(null, enabled())).isEmpty();
        assertThat(service.resolveSelected(List.of("  ", ""), enabled())).isEmpty();
        // No selection never touches the switch: a disabled deployment still answers normally.
        assertThat(service.resolveSelected(List.of(), disabled())).isEmpty();
    }

    @Test
    void catalogIsHiddenWhenTheAdministratorSwitchedSkillsOff() {
        assertThat(service.catalog(disabled())).isEmpty();
        assertThat(service.catalog(enabled())).extracting(Skill::name)
                .contains("config-walkthrough", "troubleshooting", "command-reference");
    }

    @Test
    void anUnknownNameIsACoded400() {
        assertThatThrownBy(() -> service.resolveSelected(List.of("missing"), enabled()))
                .isInstanceOfSatisfying(SkillUnknownException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(SkillUnknownException.CODE);
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getMessage()).contains("missing");
                });
    }

    @Test
    void selectingWhileSkillsAreOffIsACoded503() {
        assertThatThrownBy(() -> service.resolveSelected(List.of("troubleshooting"), disabled()))
                .isInstanceOfSatisfying(SkillsDisabledException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(SkillsDisabledException.CODE);
                    assertThat(ex.getStatusCode().value()).isEqualTo(503);
                });
    }

    private static AiSettings enabled() {
        return settings(true);
    }

    private static AiSettings disabled() {
        return settings(false);
    }

    private static AiSettings settings(boolean skillsEnabled) {
        return new AiSettings(50_000, true, true, true, true, true, true, skillsEnabled,
                ModerationMode.LOG);
    }
}
