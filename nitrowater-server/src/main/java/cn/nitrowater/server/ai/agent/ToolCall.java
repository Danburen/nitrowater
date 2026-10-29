package cn.nitrowater.server.ai.agent;

/**
 * One tool invocation recorded during an agent turn.
 *
 * @param name tool name ({@code searchDocs}, {@code loadSkill}, {@code webSearch}, {@code runCode})
 * @param argument the main argument (query / skill name / language), already truncated
 * @param resultCount number of results produced (0 on error or no hits)
 * @param status normalised outcome (e.g. {@code ok}, {@code compile_error}, {@code queued})
 * @param summary short human-readable detail for the UI, or {@code null}
 */
public record ToolCall(String name, String argument, int resultCount, String status, String summary) {

    /**
     * Backwards-compatible constructor for tools that do not report a detailed status.
     *
     * @param name tool name
     * @param argument main argument
     * @param resultCount number of results
     */
    public ToolCall(String name, String argument, int resultCount) {
        this(name, argument, resultCount, "ok", null);
    }
}
