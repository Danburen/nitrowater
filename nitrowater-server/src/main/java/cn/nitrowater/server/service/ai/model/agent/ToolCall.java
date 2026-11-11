package cn.nitrowater.server.service.ai.model.agent;

import cn.nitrowater.server.service.ai.model.tool.ToolCode;

/**
 * One tool invocation recorded during an agent turn.
 *
 * @param name tool name ({@code searchDocs}, {@code calculate}, {@code loadSkill}, {@code webSearch},
 *             {@code runCode})
 * @param argument the main argument (query / expression / skill name), already truncated
 * @param resultCount number of results produced (0 on error or no hits)
 * @param status outcome code from {@link ToolCode#getCode()} (e.g. {@code tool.ok},
 *               {@code tool.compile_error}, {@code tool.queued})
 * @param summary short human-readable detail for the UI, or {@code null}
 * @param ok whether the call was handled; {@code null} in rows stored before the code vocabulary
 *           existed, in which case it is derived from {@code status}
 * @param render how the call is presented; rows stored before the field existed default to
 *               {@link RenderPolicy#FULL}
 */
public record ToolCall(String name, String argument, int resultCount, String status, String summary,
        Boolean ok, RenderPolicy render) {

    /**
     * Derives {@code ok} for rows persisted before the field existed, so old conversations keep
     * their correct badge instead of defaulting to "failed".
     */
    public ToolCall {
        if (ok == null) {
            ok = ToolCode.isSuccessCode(status);
        }
        if (render == null) {
            render = RenderPolicy.FULL;
        }
    }

    /**
     * @param name tool name
     * @param argument main argument
     * @param resultCount number of results
     * @param status outcome code
     * @param summary short detail for the UI
     * @param render how the call is presented
     */
    public ToolCall(String name, String argument, int resultCount, String status, String summary,
            RenderPolicy render) {
        this(name, argument, resultCount, status, summary, null, render);
    }

    /**
     * @param name tool name
     * @param argument main argument
     * @param resultCount number of results
     * @param status outcome code
     * @param summary short detail for the UI
     */
    public ToolCall(String name, String argument, int resultCount, String status, String summary) {
        this(name, argument, resultCount, status, summary, null, null);
    }

    /**
     * Backwards-compatible constructor for tools that did not report a detailed outcome.
     *
     * @param name tool name
     * @param argument main argument
     * @param resultCount number of results
     */
    public ToolCall(String name, String argument, int resultCount) {
        this(name, argument, resultCount, ToolCode.OK.getCode(), null, null, null);
    }
}
