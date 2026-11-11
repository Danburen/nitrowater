package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import org.springframework.ai.tool.ToolCallback;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * One registered tool together with the facts that decide whether a turn advertises it.
 *
 * <p>The facts are independent and must never be collapsed into each other:</p>
 * <ul>
 *   <li>{@link #available()} — the runtime dependency exists (a web-search key is configured, a
 *       sandbox is reachable); it says nothing about permission;</li>
 *   <li>{@link #enabled(AiSettings, Set)} — the administrator left the feature switch on and did not
 *       hide this tool; it says nothing about the user;</li>
 *   <li>{@link #optIn()} — the user must leave the tool on for this turn; it says nothing about
 *       origin (the default is on, so only an explicit opt-out withholds it);</li>
 *   <li>{@link #timeoutMs()} — the per-call deadline;</li>
 *   <li>{@link #render()} — how the call is shown to the user. Also independent: it changes neither
 *       the model-facing schema nor the trace nor the budget.</li>
 * </ul>
 *
 * <p>{@link #advertised(AiSettings, Set, boolean)} is the only combination rule, so a new gate is
 * added here once instead of in every caller.</p>
 *
 * @param callback Spring AI callback: name, description, input schema and invocation
 * @param feature administrator switch that must be on
 * @param optIn whether the user has to opt in for this turn
 * @param probe runtime precondition, evaluated per turn; keep it a supplier because tests and
 *        deployments flip the underlying configuration without rebuilding the registry
 * @param timeoutMs hard deadline for one invocation; {@code 0} means the tool enforces its own
 *        budget (the sandbox and the web-search provider already do), so no second clock is added
 * @param render how the invocation is presented to the user
 */
public record AgentTool(ToolCallback callback, ToolFeature feature, boolean optIn,
        BooleanSupplier probe, int timeoutMs, RenderPolicy render) {

    /** Tools that are usable whenever their bean exists. */
    private static final BooleanSupplier ALWAYS = () -> true;

    /** Defaults keep old call sites unchanged and every existing tool a full card. */
    public AgentTool {
        if (render == null) {
            render = RenderPolicy.FULL;
        }
    }

    /**
     * Registers a tool with no runtime precondition of its own.
     *
     * @param callback  Spring AI callback
     * @param feature   administrator switch it belongs to
     * @param optIn     whether the user must opt in per turn
     * @param timeoutMs per-call deadline, or {@code 0} when the tool owns its budget
     * @param render    how the invocation is presented to the user
     */
    public AgentTool(ToolCallback callback, ToolFeature feature, boolean optIn, int timeoutMs,
            RenderPolicy render) {
        this(callback, feature, optIn, ALWAYS, timeoutMs, render);
    }

    /**
     * @param callback  Spring AI callback
     * @param feature   administrator switch it belongs to
     * @param optIn     whether the user must opt in per turn
     * @param timeoutMs per-call deadline, or {@code 0} when the tool owns its budget
     */
    public AgentTool(ToolCallback callback, ToolFeature feature, boolean optIn, int timeoutMs) {
        this(callback, feature, optIn, ALWAYS, timeoutMs, RenderPolicy.FULL);
    }

    /**
     * @param callback  Spring AI callback
     * @param feature   administrator switch it belongs to
     * @param optIn     whether the user must opt in per turn
     * @param probe     runtime precondition
     * @param timeoutMs per-call deadline, or {@code 0} when the tool owns its budget
     */
    public AgentTool(ToolCallback callback, ToolFeature feature, boolean optIn, BooleanSupplier probe,
            int timeoutMs) {
        this(callback, feature, optIn, probe, timeoutMs, RenderPolicy.FULL);
    }

    /** @return registered tool name, as advertised to the model */
    public String name() {
        return callback.getToolDefinition().name();
    }

    /** @return whether the tool's runtime dependency is usable right now */
    public boolean available() {
        return probe.getAsBoolean();
    }

    /**
     * Whether configuration allows the tool, ignoring who is asking.
     *
     * @param settings      current runtime switches
     * @param disabledTools names the administrator hid explicitly
     * @return whether the feature is on and the tool is not hidden
     */
    public boolean enabled(AiSettings settings, Set<String> disabledTools) {
        return feature.enabled(settings) && !disabledTools.contains(name());
    }

    /**
     * @param settings      current runtime switches
     * @param disabledTools names the administrator hid explicitly
     * @param optInGranted  whether the user left the tool on for this turn (on by default)
     * @return whether the tool may be advertised for this turn
     */
    public boolean advertised(AiSettings settings, Set<String> disabledTools, boolean optInGranted) {
        return available() && enabled(settings, disabledTools) && (!optIn || optInGranted);
    }
}
