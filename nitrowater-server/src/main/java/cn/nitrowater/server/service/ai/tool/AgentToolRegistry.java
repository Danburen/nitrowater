package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.server.service.ai.model.AiSettings;
import cn.nitrowater.server.service.ai.model.agent.AgentTool;
import cn.nitrowater.server.service.ai.model.agent.ToolFeature;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The single registry of every tool the agent may be offered.
 *
 * <p>Tools are code: each tool bean is scanned once at start-up for its {@code @Tool} methods and
 * then held as a flat list together with its gates. Which subset a turn advertises is derived per
 * request from the persisted switches, the administrator's disable list, the tool's own runtime
 * precondition and the user's opt-in — adding a tool is one line here.</p>
 *
 * <p>The registry is also the only place that answers "is this name a tool?", which keeps the admin
 * catalog, the {@code /status} card and the agent request builder from each re-implementing the
 * same filter.</p>
 */
@Component
public class AgentToolRegistry {

    /**
     * Retrieval embeds the query and hits the vector store; a healthy index answers in well under a
     * second, so anything slower is a stall the turn should not wait out.
     */
    private static final int RETRIEVAL_TIMEOUT_MS = 25_000;

    /** Plain database lookups: past ten seconds the database, not the tool, is the problem. */
    private static final int LOOKUP_TIMEOUT_MS = 10_000;

    /** The tool already enforces its own budget, so the wrapper must not add a second clock. */
    private static final int OWN_BUDGET = 0;

    private final List<AgentTool> tools;

    public AgentToolRegistry(DocTools docTools, SkillTools skillTools, RunCodeTool runCodeTool,
            MemoryTools memoryTools, WebSearchTool webSearchTool,
            WebSearchProvider webSearchProvider) {
        List<AgentTool> registered = new ArrayList<>();
        registered.addAll(scan(docTools, ToolFeature.DOCS, false, RETRIEVAL_TIMEOUT_MS));
        registered.addAll(scan(skillTools, ToolFeature.SKILLS, false, LOOKUP_TIMEOUT_MS));
        registered.addAll(scan(runCodeTool, ToolFeature.CODE, false, OWN_BUDGET));
        registered.addAll(scan(memoryTools, ToolFeature.NONE, false, LOOKUP_TIMEOUT_MS));
        // Web search is the one tool whose availability depends on deployment configuration, so it
        // owns a probe instead of being assumed usable. Callers no longer have to fold "provider
        // configured" into the user's opt-in.
        registered.addAll(scan(webSearchTool, ToolFeature.WEB, true,
                webSearchProvider::enabled, OWN_BUDGET));
        this.tools = List.copyOf(registered);
    }

    /** @return every registered tool, in registration order */
    public List<AgentTool> all() {
        return tools;
    }

    /**
     * @param name registered tool name
     * @return the tool, or empty when nothing is registered under that name
     */
    public Optional<AgentTool> find(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
    }

    /**
     * Tools to advertise for one turn.
     *
     * @param settings runtime switches of the turn
     * @param disabledTools names the administrator hid for everyone
     * @param optInGranted whether the user consented for this turn
     * @return tools in registration order, with their per-call deadlines
     */
    public List<AgentTool> forTurn(AiSettings settings, Set<String> disabledTools,
            boolean optInGranted) {
        return tools.stream()
                .filter(tool -> tool.advertised(settings, disabledTools, optInGranted))
                .toList();
    }

    /**
     * Expands one tool bean into tools; a bean may expose more than one {@code @Tool} method.
     *
     * @param bean tool bean
     * @param feature administrator switch it belongs to
     * @param optIn whether the user must opt in per turn
     * @param timeoutMs per-call deadline, or {@code 0} when the tool owns its budget
     * @return registered tools for that bean
     */
    private static List<AgentTool> scan(Object bean, ToolFeature feature, boolean optIn,
            int timeoutMs) {
        return scan(bean, feature, optIn, () -> true, timeoutMs);
    }

    /**
     * @param bean tool bean
     * @param feature administrator switch it belongs to
     * @param optIn whether the user must opt in per turn
     * @param probe runtime precondition of the tool
     * @param timeoutMs per-call deadline, or {@code 0} when the tool owns its budget
     * @return registered tools for that bean
     */
    private static List<AgentTool> scan(Object bean, ToolFeature feature, boolean optIn,
            BooleanSupplier probe, int timeoutMs) {
        return Arrays.stream(
                        MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks())
                .map(callback -> new AgentTool(callback, feature, optIn, probe, timeoutMs))
                .toList();
    }
}
