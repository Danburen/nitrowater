package cn.nitrowater.server.web.dto;

/**
 * A tool exposed to the agent (admin catalog view).
 *
 * @param name tool name
 * @param description tool description shown to the model
 * @param source {@code core} or {@code web}
 * @param enabled whether it may be advertised for a request
 * @param inputSchema JSON schema of the tool arguments
 */
public record AiToolResp(String name, String description, String source, boolean enabled,
        String inputSchema) {
}
