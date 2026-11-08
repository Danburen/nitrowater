package cn.nitrowater.server.service.ai.model.tool;

/**
 * One tool of the catalog advertised to the agent.
 *
 * <p>Three independent facts are reported instead of one merged flag, because the console has to
 * tell "the administrator hid it" apart from "the user must consent" and from "the deployment
 * cannot run it". Merging them (as a single {@code enabled}) makes the reasons indistinguishable
 * and forces every new gate to be re-encoded in the same boolean.</p>
 *
 * @param name tool name
 * @param description description shown to the model
 * @param source catalog bucket of the tool's {@code ToolFeature}: {@code core}, {@code docs},
 *        {@code skills}, {@code code} or {@code web}; a new feature adds a new value, it never
 *        reshuffles the existing ones
 * @param enabled whether configuration allows it: the feature switch is on and the administrator
 *        did not hide the tool
 * @param optIn whether the user must consent for this turn
 * @param available whether the tool's runtime dependency is usable (e.g. a web-search key is set)
 * @param inputSchema JSON schema of the tool arguments
 */
public record AiToolInfo(
        String name,
        String description,
        String source,
        boolean enabled,
        boolean optIn,
        boolean available,
        String inputSchema
) {

}
