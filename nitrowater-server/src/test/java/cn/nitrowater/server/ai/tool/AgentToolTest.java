package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.agent.AgentTool;
import cn.nitrowater.server.service.ai.model.agent.ToolFeature;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gates on a registered tool: runtime availability, feature switch, administrator disable list
 * and per-turn opt-in.
 *
 * <p>These are independent, so every combination is asserted here instead of through the agent,
 * where a missing gate would only show up as a tool the model can call when it should not.</p>
 */
class AgentToolTest {

    private static final AiSettings ALL_ON = new AiSettings(1000, true, true, true, true, true, true,
            true, ModerationMode.LOG);

    private static final AiSettings ALL_OFF = new AiSettings(1000, true, true, true, false, false,
            false, false, ModerationMode.LOG);

    @Test
    void advertisesWhenFeatureIsOnAndNobodyDisabledIt() {
        AgentTool tool = tool("searchDocs", ToolFeature.DOCS, false);

        assertThat(tool.advertised(ALL_ON, Set.of(), false)).isTrue();
        assertThat(tool.advertised(ALL_ON, Set.of("searchDocs"), false)).isFalse();
        assertThat(tool.advertised(ALL_OFF, Set.of(), false)).isFalse();
    }

    @Test
    void optInToolNeedsTheTurnGrant() {
        AgentTool tool = tool("webSearch", ToolFeature.WEB, true);

        assertThat(tool.advertised(ALL_ON, Set.of(), false)).isFalse();
        assertThat(tool.advertised(ALL_ON, Set.of(), true)).isTrue();
        assertThat(tool.advertised(ALL_OFF, Set.of(), true)).isFalse();
        assertThat(tool.advertised(ALL_ON, Set.of("webSearch"), true)).isFalse();
    }

    @Test
    void featurelessToolIsSubjectOnlyToTheDisableList() {
        AgentTool tool = tool("saveMemory", ToolFeature.NONE, false);

        assertThat(tool.advertised(ALL_OFF, Set.of(), false)).isTrue();
        assertThat(tool.advertised(ALL_OFF, Set.of("saveMemory"), false)).isFalse();
    }

    /**
     * The bucket names the capability, never the gate: `optIn` is not a second taxonomy, so a tool
     * that needs consent is not automatically "web" and a web tool that does not need it is not
     * "core".
     */
    @Test
    void catalogBucketComesFromTheFeatureNotFromTheOptInGate() {
        assertThat(tool("searchDocs", ToolFeature.DOCS, false).feature().bucket()).isEqualTo("docs");
        assertThat(tool("webSearch", ToolFeature.WEB, true).feature().bucket()).isEqualTo("web");
        assertThat(tool("saveMemory", ToolFeature.NONE, true).feature().bucket()).isEqualTo("core");
    }

    /** The console needs configuration state on its own, independent of who is asking. */
    @Test
    void enabledReportsConfigurationWithoutAskingWhoIsCalling() {
        AgentTool tool = tool("webSearch", ToolFeature.WEB, true);

        assertThat(tool.enabled(ALL_ON, Set.of())).isTrue();
        assertThat(tool.enabled(ALL_OFF, Set.of())).isFalse();
        assertThat(tool.enabled(ALL_ON, Set.of("webSearch"))).isFalse();
    }

    @Test
    void missingRuntimeDependencyHidesTheToolEvenWhenEverythingElseIsOn() {
        AgentTool tool = tool("webSearch", ToolFeature.WEB, true, () -> false);

        assertThat(tool.available()).isFalse();
        assertThat(tool.advertised(ALL_ON, Set.of(), true)).isFalse();
    }

    private static AgentTool tool(String name, ToolFeature feature, boolean optIn) {
        return tool(name, feature, optIn, () -> true);
    }

    private static AgentTool tool(String name, ToolFeature feature, boolean optIn,
            BooleanSupplier probe) {
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(name)
                .description(name + " description")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        return new AgentTool(new FixedCallback(definition), feature, optIn, probe, 0);
    }

    /** Minimal callback: the gate only needs the definition, never an invocation. */
    private record FixedCallback(ToolDefinition definition) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new UnsupportedOperationException("not invoked by the gate");
        }
    }
}
