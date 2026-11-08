package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.ai.model.tool.AiToolInfo;

/**
 * A tool exposed to the agent (admin catalog view).
 *
 * @param name tool name
 * @param description tool description shown to the model
 * @param source catalog bucket ({@code core} / {@code docs} / {@code skills} / {@code code} /
 *        {@code web}); it names the capability, not the permission
 * @param enabled whether configuration allows it (feature switch on, not hidden by an administrator)
 * @param optIn whether the user must consent for this turn
 * @param available whether the tool's runtime dependency is usable right now
 * @param inputSchema JSON schema of the tool arguments
 */
public record AiToolResp(String name, String description, String source, boolean enabled,
        boolean optIn, boolean available, String inputSchema) {

    /**
     * @param tool service view
     * @return the wire view
     */
    public static AiToolResp from(AiToolInfo tool) {
        return new AiToolResp(tool.name(), tool.description(), tool.source(), tool.enabled(),
                tool.optIn(), tool.available(), tool.inputSchema());
    }
}
