package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.model.AiSettings;

/**
 * The administrator switch a tool belongs to.
 *
 * <p>Registering a tool with its feature keeps the tool name out of {@link AiSettings}: the settings
 * model only knows the switches, never which tool uses which one. {@link #NONE} marks a tool that
 * needs no switch and is available as soon as the runtime is on.</p>
 */
public enum ToolFeature {

    /** No dedicated switch: available whenever the agent itself is enabled. */
    NONE,

    /** {@code searchDocs}. */
    DOCS,

    /** {@code loadSkill}. */
    SKILLS,

    /** {@code runCode}. */
    CODE,

    /** {@code webSearch}. */
    WEB;

    /**
     * @param settings current runtime switches
     * @return whether this feature is switched on
     */
    public boolean enabled(AiSettings settings) {
        return switch (this) {
            case DOCS -> settings.documentSearchEnabled();
            case SKILLS -> settings.skillsEnabled();
            case CODE -> settings.codeRunnerEnabled();
            case WEB -> settings.webSearchEnabled();
            case NONE -> true;
        };
    }

    /**
     * The catalog bucket a tool is filed under, i.e. the capability it belongs to.
     *
     * <p>This is a <em>taxonomy</em>, not a gate: it says where the tool comes from, never who may
     * call it. Keeping it here rather than deriving it from a gate means adding a feature gives the
     * console a new bucket for free, while tool names never enter {@link AiSettings}.</p>
     *
     * @return the value sent as {@code source} on the admin catalog wire
     */
    public String bucket() {
        return switch (this) {
            case DOCS -> "docs";
            case SKILLS -> "skills";
            case CODE -> "code";
            case WEB -> "web";
            case NONE -> "core";
        };
    }
}
