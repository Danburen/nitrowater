package cn.nitrowater.server.service.ai.model.agent;

/**
 * How one tool call is presented to the user.
 *
 * <p>Visibility is a transport/UI concern only: the model always sees the same schema, the call is
 * always recorded on the {@link AgentTrace}, and budgets never depend on it. Keeping it out of the
 * prompt is deliberate — telling the model that a tool is "hidden" changes nothing it can act on
 * and only leaks implementation detail.</p>
 *
 * <p>Mirrors the industry split (AI SDK tool parts, LangGraph's {@code tools} channel, Anthropic's
 * {@code response_inclusion}): everything is a tool call, the client decides how much of it to
 * show.</p>
 */
public enum RenderPolicy {

    /** Full step card: external calls, mutations, anything the user should be able to audit. */
    FULL,

    /** One-line chip: deterministic local helpers whose value is visible but whose card is noise. */
    COMPACT,

    /**
     * Traced but not rendered: results consumed inside the answer pipeline rather than shown as an
     * action. Never use it to hide a computation the user asked for.
     */
    EXCLUDED
}
